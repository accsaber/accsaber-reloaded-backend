package com.accsaber.backend.service.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.map.BulkReweightRequest;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.DifficultyRow;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.EstimateInfo;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.LadderValues;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.MapLeaderboard;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.MapValues;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.PlayValues;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.PlayerBoard;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.PlayerRow;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.ScoreRow;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.TotalValues;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.ComplexityEstimateSource;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyComplexityEstimate;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.map.ComplexityScenarioService.Ladder;
import com.accsaber.backend.service.map.ComplexityScenarioService.MapAggregate;
import com.accsaber.backend.service.map.ComplexityScenarioService.Play;
import com.accsaber.backend.service.map.ComplexityScenarioService.PlayerTotal;
import com.accsaber.backend.service.map.ComplexityScenarioService.ScenarioState;
import com.accsaber.backend.util.Rounding;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ComplexityComparisonService {

    private static final int SCALE = 6;

    private final MapDifficultyRepository mapDifficultyRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final ComplexityScenarioService scenarioService;
    private final ComplexityEstimateService estimateService;
    private final ReweightService reweightService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public List<DifficultyRow> difficulties(UUID categoryId, MapDifficultyStatus status) {
        List<MapDifficulty> difficulties = mapDifficultyRepository.findByStatusAndActiveTrueWithCategory(status)
                .stream()
                .filter(d -> categoryId == null || (d.getCategory() != null && categoryId.equals(d.getCategory().getId())))
                .sorted(Comparator.comparing(d -> d.getMap().getSongName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        return rows(difficulties);
    }

    @Transactional(readOnly = true)
    public List<DifficultyRow> highestAverageAp(ComplexityScenario scenario, UUID categoryId, int minScores,
            int limit) {
        ScenarioState state = scenarioService.state(scenario);
        List<UUID> ids = state.aggregates().entrySet().stream()
                .filter(e -> e.getValue().scores() >= minScores)
                .sorted(Comparator.comparing((Map.Entry<UUID, MapAggregate> e) -> e.getValue().averageWeightedAp())
                        .reversed())
                .map(Map.Entry::getKey)
                .toList();
        Map<UUID, MapDifficulty> byId = mapDifficultyRepository.findAllByIdInAndActiveTrueWithMapAndCategory(ids)
                .stream().collect(Collectors.toMap(MapDifficulty::getId, Function.identity()));
        List<MapDifficulty> ordered = ids.stream().map(byId::get)
                .filter(d -> d != null && (categoryId == null
                        || (d.getCategory() != null && categoryId.equals(d.getCategory().getId()))))
                .limit(limit)
                .toList();
        return rows(ordered);
    }

    @Transactional(readOnly = true)
    public MapLeaderboard leaderboard(UUID mapDifficultyId) {
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrueWithMapAndCategory(mapDifficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", mapDifficultyId));
        DifficultyRow header = rows(List.of(difficulty)).get(0);
        Map<ComplexityScenario, Map<Long, Play>> playsByScenario = new EnumMap<>(ComplexityScenario.class);
        for (ComplexityScenario scenario : ComplexityScenario.values()) {
            playsByScenario.put(scenario, scenarioService.state(scenario).playsByDifficulty()
                    .getOrDefault(mapDifficultyId, List.of()).stream()
                    .collect(Collectors.toMap(Play::userId, Function.identity())));
        }
        Map<Long, Play> current = playsByScenario.get(ComplexityScenario.CURRENT);
        Map<Long, User> users = users(current.keySet());
        List<ScoreRow> rows = current.values().stream()
                .sorted(Comparator.comparingInt(Play::rank))
                .map(play -> scoreRow(play, users.get(play.userId()), playsByScenario))
                .toList();
        return MapLeaderboard.builder().difficulty(header).rows(rows).build();
    }

    @Transactional(readOnly = true)
    public PlayerBoard players(UUID categoryId, int limit) {
        Category category = categoryId == null ? null
                : categoryRepository.findByIdAndActiveTrue(categoryId)
                        .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
        Map<ComplexityScenario, Map<Long, PlayerTotal>> totals = new EnumMap<>(ComplexityScenario.class);
        Map<ComplexityScenario, LadderValues> ladders = new EnumMap<>(ComplexityScenario.class);
        for (ComplexityScenario scenario : ComplexityScenario.values()) {
            ScenarioState state = scenarioService.state(scenario);
            totals.put(scenario, category == null ? state.overallTotals()
                    : state.totalsByCategory().getOrDefault(category.getId(), Map.of()));
            Ladder ladder = category == null ? state.overallLadder() : state.ladders().get(category.getId());
            if (ladder != null) {
                ladders.put(scenario, ladderValues(ladder));
            }
        }
        Map<Long, PlayerTotal> current = totals.get(ComplexityScenario.CURRENT);
        List<Long> order = current.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> e.getValue().rank()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
        Map<Long, User> users = users(new HashSet<>(order));
        List<PlayerRow> rows = order.stream().map(userId -> playerRow(userId, users.get(userId), totals)).toList();
        return PlayerBoard.builder()
                .categoryId(category == null ? null : category.getId())
                .categoryCode(category == null ? "overall" : category.getCode())
                .ladders(ladders)
                .rows(rows)
                .build();
    }

    public void apply(ComplexityScenario scenario, String reason, Long staffUserId, UUID staffId) {
        if (scenario.source() == null) {
            throw new ValidationException("Only an estimated scenario can be applied");
        }
        Map<UUID, Double> current = scenarioService.complexitiesFor(ComplexityScenario.CURRENT);
        Map<UUID, Double> proposed = scenarioService.complexitiesFor(scenario);
        List<BulkReweightRequest.Item> items = new ArrayList<>();
        proposed.forEach((id, complexity) -> {
            Double now = current.get(id);
            if (now != null && Math.abs(now - complexity) > 1e-9) {
                BulkReweightRequest.Item item = new BulkReweightRequest.Item();
                item.setMapDifficultyId(id);
                item.setComplexity(complexity);
                items.add(item);
            }
        });
        if (items.isEmpty()) {
            throw new ValidationException("The " + scenario + " scenario matches the current complexities");
        }
        reweightService.bulkReweight(items, reason, staffUserId, staffId);
    }

    private List<DifficultyRow> rows(List<MapDifficulty> difficulties) {
        List<UUID> ids = difficulties.stream().map(MapDifficulty::getId).toList();
        Map<UUID, Map<ComplexityEstimateSource, MapDifficultyComplexityEstimate>> estimates = estimateService
                .estimatesFor(ids);
        Map<ComplexityScenario, ScenarioState> states = ComplexityScenarioService.all(scenarioService);
        return difficulties.stream().map(d -> difficultyRow(d, states, estimates.getOrDefault(d.getId(), Map.of())))
                .toList();
    }

    private DifficultyRow difficultyRow(MapDifficulty d, Map<ComplexityScenario, ScenarioState> states,
            Map<ComplexityEstimateSource, MapDifficultyComplexityEstimate> estimates) {
        Map<ComplexityScenario, MapValues> scenarios = new EnumMap<>(ComplexityScenario.class);
        Map<ComplexityScenario, EstimateInfo> info = new EnumMap<>(ComplexityScenario.class);
        int scores = 0;
        for (ComplexityScenario scenario : ComplexityScenario.values()) {
            ScenarioState state = states.get(scenario);
            MapAggregate aggregate = state.aggregates().get(d.getId());
            Double complexity = state.complexities().get(d.getId());
            MapDifficultyComplexityEstimate estimate = scenario.source() == null ? null
                    : estimates.get(scenario.source());
            if (complexity == null && estimate != null) {
                complexity = estimate.getComplexity();
            }
            scenarios.put(scenario, MapValues.builder()
                    .complexity(complexity)
                    .topAp(aggregate == null ? null : aggregate.topAp())
                    .averageAp(aggregate == null ? null : aggregate.averageAp())
                    .averageWeightedAp(aggregate == null ? null : aggregate.averageWeightedAp())
                    .build());
            if (aggregate != null) {
                scores = aggregate.scores();
            }
            if (estimate != null) {
                info.put(scenario, EstimateInfo.builder()
                        .version(estimate.getVersion())
                        .updatedAt(estimate.getUpdatedAt())
                        .inputs(objectMapper.convertValue(estimate.getInputs(), new TypeReference<Map<String, Object>>() {
                        }))
                        .build());
            }
        }
        return DifficultyRow.builder()
                .mapDifficultyId(d.getId())
                .mapId(d.getMap().getId())
                .songName(d.getMap().getSongName())
                .songSubName(d.getMap().getSongSubName())
                .songAuthor(d.getMap().getSongAuthor())
                .mapAuthor(d.getMap().getMapAuthor())
                .coverUrl(d.getMap().getCoverUrl())
                .cdnCoverUrl(d.getMap().getCdnCoverUrl())
                .difficulty(d.getDifficulty() == null ? null : d.getDifficulty().name())
                .characteristic(d.getCharacteristic())
                .categoryId(d.getCategory() == null ? null : d.getCategory().getId())
                .categoryCode(d.getCategory() == null ? null : d.getCategory().getCode())
                .status(d.getStatus())
                .scores(scores)
                .scenarios(scenarios)
                .deltas(mapDeltas(scenarios))
                .estimates(info)
                .build();
    }

    private static Map<ComplexityScenario, MapValues> mapDeltas(Map<ComplexityScenario, MapValues> scenarios) {
        MapValues base = scenarios.get(ComplexityScenario.CURRENT);
        Map<ComplexityScenario, MapValues> deltas = new EnumMap<>(ComplexityScenario.class);
        for (var entry : scenarios.entrySet()) {
            if (entry.getKey() == ComplexityScenario.CURRENT) {
                continue;
            }
            MapValues v = entry.getValue();
            deltas.put(entry.getKey(), MapValues.builder()
                    .complexity(diff(v.getComplexity(), base.getComplexity()))
                    .topAp(diff(v.getTopAp(), base.getTopAp()))
                    .averageAp(diff(v.getAverageAp(), base.getAverageAp()))
                    .averageWeightedAp(diff(v.getAverageWeightedAp(), base.getAverageWeightedAp()))
                    .build());
        }
        return deltas;
    }

    private static ScoreRow scoreRow(Play current, User user, Map<ComplexityScenario, Map<Long, Play>> plays) {
        Map<ComplexityScenario, PlayValues> scenarios = new EnumMap<>(ComplexityScenario.class);
        Map<ComplexityScenario, PlayValues> deltas = new EnumMap<>(ComplexityScenario.class);
        for (var entry : plays.entrySet()) {
            Play play = entry.getValue().get(current.userId());
            PlayValues values = play == null ? PlayValues.builder().build()
                    : PlayValues.builder().ap(play.ap()).weightedAp(play.weightedAp()).rank(play.rank()).build();
            scenarios.put(entry.getKey(), values);
            if (entry.getKey() != ComplexityScenario.CURRENT) {
                deltas.put(entry.getKey(), PlayValues.builder()
                        .ap(diff(values.getAp(), current.ap()))
                        .weightedAp(diff(values.getWeightedAp(), current.weightedAp()))
                        .rank(values.getRank() == null ? null : values.getRank() - current.rank())
                        .build());
            }
        }
        return ScoreRow.builder()
                .userId(String.valueOf(current.userId()))
                .name(user == null ? "" : user.getName())
                .avatarUrl(user == null ? null : user.getAvatarUrl())
                .cdnAvatarUrl(user == null ? null : user.getCdnAvatarUrl())
                .country(user == null ? null : user.getCountry())
                .accuracy(current.accuracy())
                .scenarios(scenarios)
                .deltas(deltas)
                .build();
    }

    private static PlayerRow playerRow(Long userId, User user, Map<ComplexityScenario, Map<Long, PlayerTotal>> totals) {
        PlayerTotal current = totals.get(ComplexityScenario.CURRENT).get(userId);
        Map<ComplexityScenario, TotalValues> scenarios = new EnumMap<>(ComplexityScenario.class);
        Map<ComplexityScenario, TotalValues> deltas = new EnumMap<>(ComplexityScenario.class);
        for (var entry : totals.entrySet()) {
            PlayerTotal total = entry.getValue().get(userId);
            TotalValues values = total == null ? TotalValues.builder().build()
                    : TotalValues.builder().ap(total.ap()).rank(total.rank()).build();
            scenarios.put(entry.getKey(), values);
            if (entry.getKey() != ComplexityScenario.CURRENT) {
                deltas.put(entry.getKey(), TotalValues.builder()
                        .ap(diff(values.getAp(), current.ap()))
                        .rank(values.getRank() == null ? null : values.getRank() - current.rank())
                        .build());
            }
        }
        return PlayerRow.builder()
                .userId(String.valueOf(userId))
                .name(user == null ? "" : user.getName())
                .avatarUrl(user == null ? null : user.getAvatarUrl())
                .cdnAvatarUrl(user == null ? null : user.getCdnAvatarUrl())
                .country(user == null ? null : user.getCountry())
                .scenarios(scenarios)
                .deltas(deltas)
                .build();
    }

    private static LadderValues ladderValues(Ladder ladder) {
        return LadderValues.builder()
                .players(ladder.players())
                .totalAp(ladder.totalAp())
                .playersWith900(ladder.playersWith900())
                .playersWith1000(ladder.playersWith1000())
                .playersWith1100(ladder.playersWith1100())
                .playsWith1000(ladder.playsWith1000())
                .playsWith1100(ladder.playsWith1100())
                .topPlayAp(ladder.topPlayAp())
                .build();
    }

    private Map<Long, User> users(Set<Long> ids) {
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private static Double diff(Double value, Double base) {
        if (value == null || base == null) {
            return null;
        }
        return Rounding.round(value - base, SCALE);
    }
}
