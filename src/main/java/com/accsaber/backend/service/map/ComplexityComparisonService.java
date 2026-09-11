package com.accsaber.backend.service.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.config.ComplexityRaterProperties;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.map.BulkReweightRequest;
import com.accsaber.backend.model.dto.request.map.ComplexityRaterSpec;
import com.accsaber.backend.model.dto.request.map.UpdateMapComplexityRequest;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.DifficultyRow;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.EstimateInfo;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.LadderValues;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.MapLeaderboard;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.MapValues;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.CategoryPlays;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.PlayRow;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.PlayValues;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.PlayerPlays;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.PlayerBoard;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.PlayerRow;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.Preview;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.Rater;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.ScoreRow;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.TotalValues;
import com.accsaber.backend.model.entity.Category;
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
import com.accsaber.backend.util.SearchText;
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
    private final ComplexityRaterProperties raterProperties;
    private final ReweightService reweightService;
    private final MapService mapService;
    private final MapDifficultyComplexityService complexityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record MapFilter(UUID categoryId, MapDifficultyStatus status, UUID batchId, String search) {
    }

    public record PlayerQuery(UUID categoryId, int limit, String search) {
    }

    @Transactional(readOnly = true)
    public List<DifficultyRow> difficulties(MapFilter filter) {
        return rows(difficultiesOf(filter), scenarioService.stored());
    }

    @Transactional(readOnly = true)
    public MapLeaderboard leaderboard(UUID mapDifficultyId) {
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrueWithMapAndCategory(mapDifficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", mapDifficultyId));
        Map<ComplexityScenario, ScenarioState> states = scenarioService.stored();
        DifficultyRow header = rows(List.of(difficulty), states).get(0);
        Map<ComplexityScenario, Map<Long, Play>> playsByScenario = new EnumMap<>(ComplexityScenario.class);
        states.forEach((scenario, state) -> playsByScenario.put(scenario, state.playsByDifficulty()
                .getOrDefault(mapDifficultyId, List.of()).stream()
                .collect(Collectors.toMap(Play::userId, Function.identity()))));
        Map<Long, Play> current = playsByScenario.get(ComplexityScenario.CURRENT);
        Map<Long, User> users = users(current.keySet());
        List<ScoreRow> rows = current.values().stream()
                .sorted(Comparator.comparingInt(Play::rank))
                .map(play -> scoreRow(play, users.get(play.userId()), playsByScenario))
                .toList();
        return MapLeaderboard.builder().difficulty(header).rows(rows).build();
    }

    @Transactional(readOnly = true)
    public PlayerBoard players(PlayerQuery query) {
        return players(scenarioService.stored(), query);
    }

    public Rater rater() {
        return Rater.builder()
                .version(raterProperties.getVersion())
                .worstBands(Arrays.stream(NoteAccuracyComplexityRater.WORST_BANDS).boxed().toList())
                .rater(raterProperties.toSpec())
                .build();
    }

    @Transactional(readOnly = true)
    public Preview preview(ComplexityRaterSpec spec, MapFilter filter, int playerLimit) {
        Map<ComplexityScenario, ScenarioState> states = previewStates(spec);
        return Preview.builder()
                .rater(spec)
                .difficulties(rows(difficultiesOf(filter), states))
                .players(players(states, new PlayerQuery(filter.categoryId(), playerLimit, null)))
                .build();
    }

    @Transactional(readOnly = true)
    public PlayerPlays playerPlays(Long userId, int limit) {
        return playerPlays(scenarioService.stored(), userId, limit);
    }

    @Transactional(readOnly = true)
    public PlayerPlays previewPlayerPlays(ComplexityRaterSpec spec, Long userId, int limit) {
        return playerPlays(previewStates(spec), userId, limit);
    }

    private Map<ComplexityScenario, ScenarioState> previewStates(ComplexityRaterSpec spec) {
        Map<ComplexityScenario, ScenarioState> states = new EnumMap<>(ComplexityScenario.class);
        states.put(ComplexityScenario.CURRENT, scenarioService.state(ComplexityScenario.CURRENT));
        states.put(ComplexityScenario.PREVIEW, scenarioService.preview(previewKey(spec), () -> previewComplexities(spec)));
        return states;
    }

    private String previewKey(ComplexityRaterSpec spec) {
        try {
            return objectMapper.writeValueAsString(spec);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Could not key the preview", e);
        }
    }

    private Map<UUID, Double> previewComplexities(ComplexityRaterSpec spec) {
        Map<UUID, Double> complexities = new HashMap<>(scenarioService.complexitiesFor(ComplexityScenario.CURRENT));
        for (MapDifficultyComplexityEstimate estimate : estimateService.estimates()) {
            MapDifficulty difficulty = estimate.getMapDifficulty();
            if (difficulty.getCategory() == null) {
                continue;
            }
            NoteAccuracyComplexityRater.price(estimate.getInputs(), spec, difficulty.getCategory().getCode())
                    .ifPresent(rating -> complexities.put(difficulty.getId(), rating.complexity()));
        }
        return complexities;
    }

    private PlayerPlays playerPlays(Map<ComplexityScenario, ScenarioState> states, Long userId, int limit) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Map<ComplexityScenario, Map<UUID, Play>> plays = new EnumMap<>(ComplexityScenario.class);
        states.forEach((scenario, state) -> plays.put(scenario, state.playsByUser().getOrDefault(userId, List.of())
                .stream().collect(Collectors.toMap(Play::difficultyId, Function.identity()))));
        Set<UUID> shown = new HashSet<>();
        plays.values().forEach(byMap -> byMap.values().stream()
                .collect(Collectors.groupingBy(Play::categoryId)).values()
                .forEach(list -> list.stream().sorted(Comparator.comparingDouble(Play::ap).reversed()).limit(limit)
                        .forEach(play -> shown.add(play.difficultyId()))));
        Map<UUID, MapDifficulty> byId = mapDifficultyRepository
                .findAllByIdInAndActiveTrueWithMapAndCategory(new ArrayList<>(shown)).stream()
                .collect(Collectors.toMap(MapDifficulty::getId, Function.identity()));
        Map<UUID, DifficultyRow> headers = rows(new ArrayList<>(byId.values()), states).stream()
                .collect(Collectors.toMap(DifficultyRow::getMapDifficultyId, Function.identity()));
        Map<UUID, Play> current = plays.get(ComplexityScenario.CURRENT);
        List<CategoryPlays> categories = categoryRepository.findByActiveTrue().stream()
                .filter(category -> byId.values().stream().anyMatch(d -> d.getCategory() != null
                        && category.getId().equals(d.getCategory().getId())))
                .map(category -> {
                    Map<ComplexityScenario, TotalValues> totals = categoryTotals(states, category.getId(), userId);
                    return CategoryPlays.builder()
                            .categoryId(category.getId())
                            .categoryCode(category.getCode())
                            .scenarios(totals)
                            .deltas(totalDeltas(totals))
                            .plays(byId.values().stream()
                                    .filter(d -> d.getCategory() != null && category.getId().equals(d.getCategory().getId()))
                                    .sorted(Comparator.comparingDouble((MapDifficulty d) -> current.containsKey(d.getId())
                                            ? current.get(d.getId()).ap() : 0.0).reversed())
                                    .map(d -> playRow(headers.get(d.getId()), d.getId(), plays))
                                    .toList())
                            .build();
                })
                .toList();
        return PlayerPlays.builder()
                .userId(String.valueOf(userId))
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .cdnAvatarUrl(user.getCdnAvatarUrl())
                .country(user.getCountry())
                .categories(categories)
                .build();
    }

    private static Map<ComplexityScenario, TotalValues> categoryTotals(Map<ComplexityScenario, ScenarioState> states,
            UUID categoryId, Long userId) {
        Map<ComplexityScenario, TotalValues> totals = new EnumMap<>(ComplexityScenario.class);
        states.forEach((scenario, state) -> {
            PlayerTotal total = state.totalsByCategory().getOrDefault(categoryId, Map.of()).get(userId);
            totals.put(scenario, total == null ? TotalValues.builder().build()
                    : TotalValues.builder().ap(total.ap()).rank(total.rank()).build());
        });
        return totals;
    }

    private static Map<ComplexityScenario, TotalValues> totalDeltas(Map<ComplexityScenario, TotalValues> totals) {
        TotalValues base = totals.get(ComplexityScenario.CURRENT);
        Map<ComplexityScenario, TotalValues> deltas = new EnumMap<>(ComplexityScenario.class);
        totals.forEach((scenario, values) -> {
            if (scenario != ComplexityScenario.CURRENT) {
                deltas.put(scenario, TotalValues.builder()
                        .ap(diff(values.getAp(), base.getAp()))
                        .rank(values.getRank() == null || base.getRank() == null ? null : values.getRank() - base.getRank())
                        .build());
            }
        });
        return deltas;
    }

    private static PlayRow playRow(DifficultyRow header, UUID difficultyId, Map<ComplexityScenario, Map<UUID, Play>> plays) {
        Play current = plays.get(ComplexityScenario.CURRENT).get(difficultyId);
        Play any = plays.values().stream().map(byMap -> byMap.get(difficultyId)).filter(Objects::nonNull).findFirst()
                .orElseThrow();
        Map<ComplexityScenario, PlayValues> scenarios = new EnumMap<>(ComplexityScenario.class);
        Map<ComplexityScenario, PlayValues> deltas = new EnumMap<>(ComplexityScenario.class);
        for (var entry : plays.entrySet()) {
            PlayValues values = playValues(entry.getValue().get(difficultyId));
            scenarios.put(entry.getKey(), values);
            if (entry.getKey() != ComplexityScenario.CURRENT && current != null) {
                deltas.put(entry.getKey(), playDelta(values, current));
            }
        }
        return PlayRow.builder()
                .difficulty(header)
                .accuracy(any.accuracy())
                .scenarios(scenarios)
                .deltas(deltas)
                .build();
    }

    private static PlayValues playValues(Play play) {
        return play == null ? PlayValues.builder().build()
                : PlayValues.builder().ap(play.ap()).weightedAp(play.weightedAp()).position(play.position())
                        .rank(play.rank()).build();
    }

    private static PlayValues playDelta(PlayValues values, Play current) {
        return PlayValues.builder()
                .ap(diff(values.getAp(), current.ap()))
                .weightedAp(diff(values.getWeightedAp(), current.weightedAp()))
                .position(values.getPosition() == null ? null : values.getPosition() - current.position())
                .rank(values.getRank() == null ? null : values.getRank() - current.rank())
                .build();
    }

    public record ApplyOptions(String reason, Double maxStep, UUID batchId, MapDifficultyStatus status) {
    }

    public void apply(ApplyOptions options, Long staffUserId, UUID staffId) {
        if (options.maxStep() != null && options.maxStep() <= 0) {
            throw new ValidationException("The step limit must be above zero");
        }
        if (options.status() != null && options.status() != MapDifficultyStatus.RANKED) {
            applyToUnranked(options, staffUserId, staffId);
            return;
        }
        Map<UUID, Double> current = scenarioService.complexitiesFor(ComplexityScenario.CURRENT);
        Map<UUID, Double> proposed = scenarioService.complexitiesFor(ComplexityScenario.NEW_SCRIPT);
        Set<UUID> scope = options.batchId() == null ? null
                : mapDifficultyRepository.findByBatchIdAndActiveTrueWithCategory(options.batchId()).stream()
                        .map(MapDifficulty::getId).collect(Collectors.toSet());
        Set<UUID> pinned = new HashSet<>(mapDifficultyRepository.findPinnedDifficultyIds());
        List<BulkReweightRequest.Item> items = new ArrayList<>();
        proposed.forEach((id, complexity) -> {
            Double now = current.get(id);
            if (now == null || (scope != null && !scope.contains(id)) || pinned.contains(id)) {
                return;
            }
            double target = step(now, complexity, options.maxStep());
            if (Math.abs(now - target) > 1e-9) {
                BulkReweightRequest.Item item = new BulkReweightRequest.Item();
                item.setMapDifficultyId(id);
                item.setComplexity(target);
                items.add(item);
            }
        });
        if (items.isEmpty()) {
            throw new ValidationException("The script matches the current complexities on every map in scope");
        }
        reweightService.bulkReweight(items, options.reason(), staffUserId, staffId);
    }

    @Transactional
    void applyToUnranked(ApplyOptions options, Long staffUserId, UUID staffId) {
        List<MapDifficulty> difficulties = difficultiesOf(new MapFilter(null, options.status(), options.batchId(), null));
        List<UUID> ids = difficulties.stream().map(MapDifficulty::getId).toList();
        Map<UUID, Double> current = complexityService.findActiveComplexitiesForDifficulties(ids);
        Map<UUID, MapDifficultyComplexityEstimate> estimates = estimateService.estimatesFor(ids);
        int changed = 0;
        for (MapDifficulty difficulty : difficulties) {
            MapDifficultyComplexityEstimate estimate = estimates.get(difficulty.getId());
            if (estimate == null || difficulty.isComplexityPinned()) {
                continue;
            }
            Double now = current.get(difficulty.getId());
            double target = now == null ? estimate.getComplexity() : step(now, estimate.getComplexity(), options.maxStep());
            if (now != null && Math.abs(now - target) < 1e-9) {
                continue;
            }
            UpdateMapComplexityRequest request = new UpdateMapComplexityRequest();
            request.setComplexity(target);
            request.setReason(options.reason());
            mapService.updateComplexity(difficulty.getId(), request, staffUserId, staffId);
            changed++;
        }
        if (changed == 0) {
            throw new ValidationException("The script matches the current complexities on every map in scope");
        }
    }

    static double step(double now, double proposed, Double maxStep) {
        if (maxStep == null) {
            return proposed;
        }
        double delta = Math.max(-maxStep, Math.min(maxStep, proposed - now));
        return Rounding.round(now + delta, 1);
    }

    private List<MapDifficulty> difficultiesOf(MapFilter filter) {
        return mapDifficultyRepository.findByStatusAndActiveTrueWithCategory(filter.status()).stream()
                .filter(d -> inCategory(d, filter.categoryId()) && inBatch(d, filter.batchId()) && matches(d, filter.search()))
                .sorted(Comparator.comparing(d -> d.getMap().getSongName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static boolean matches(MapDifficulty difficulty, String search) {
        return SearchText.matches(search, difficulty.getMap().getSongName(), difficulty.getMap().getSongSubName(),
                difficulty.getMap().getSongAuthor(), difficulty.getMap().getMapAuthor());
    }

    private static boolean inBatch(MapDifficulty difficulty, UUID batchId) {
        return batchId == null || (difficulty.getBatch() != null && batchId.equals(difficulty.getBatch().getId()));
    }

    private static boolean inCategory(MapDifficulty difficulty, UUID categoryId) {
        return categoryId == null
                || (difficulty.getCategory() != null && categoryId.equals(difficulty.getCategory().getId()));
    }

    private PlayerBoard players(Map<ComplexityScenario, ScenarioState> states, PlayerQuery query) {
        UUID categoryId = query.categoryId();
        Category category = categoryId == null ? null
                : categoryRepository.findByIdAndActiveTrue(categoryId)
                        .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
        Set<Long> matching = query.search() == null || query.search().isBlank() ? null
                : new HashSet<>(userRepository.findIdsBySearch(query.search().trim()));
        Map<ComplexityScenario, Map<Long, PlayerTotal>> totals = new EnumMap<>(ComplexityScenario.class);
        Map<ComplexityScenario, LadderValues> ladders = new EnumMap<>(ComplexityScenario.class);
        states.forEach((scenario, state) -> {
            totals.put(scenario, category == null ? state.overallTotals()
                    : state.totalsByCategory().getOrDefault(category.getId(), Map.of()));
            Ladder ladder = category == null ? state.overallLadder() : state.ladders().get(category.getId());
            if (ladder != null) {
                ladders.put(scenario, ladderValues(ladder));
            }
        });
        Map<Long, PlayerTotal> current = totals.get(ComplexityScenario.CURRENT);
        List<Long> order = current.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> e.getValue().rank()))
                .filter(e -> matching == null || matching.contains(e.getKey()))
                .limit(query.limit())
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

    private List<DifficultyRow> rows(List<MapDifficulty> difficulties, Map<ComplexityScenario, ScenarioState> states) {
        List<UUID> ids = difficulties.stream().map(MapDifficulty::getId).toList();
        Map<UUID, MapDifficultyComplexityEstimate> estimates = estimateService.estimatesFor(ids);
        return difficulties.stream()
                .map(d -> difficultyRow(d, states, estimates.get(d.getId())))
                .toList();
    }

    private DifficultyRow difficultyRow(MapDifficulty d, Map<ComplexityScenario, ScenarioState> states,
            MapDifficultyComplexityEstimate estimate) {
        Map<ComplexityScenario, MapValues> scenarios = new EnumMap<>(ComplexityScenario.class);
        Map<ComplexityScenario, EstimateInfo> info = new EnumMap<>(ComplexityScenario.class);
        int scores = 0;
        for (var entry : states.entrySet()) {
            ComplexityScenario scenario = entry.getKey();
            ScenarioState state = entry.getValue();
            MapAggregate aggregate = state.aggregates().get(d.getId());
            Double complexity = state.complexities().get(d.getId());
            MapDifficultyComplexityEstimate stored = scenario == ComplexityScenario.NEW_SCRIPT ? estimate : null;
            if (complexity == null && stored != null) {
                complexity = stored.getComplexity();
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
            if (stored != null) {
                info.put(scenario, EstimateInfo.builder()
                        .version(stored.getVersion())
                        .updatedAt(stored.getUpdatedAt())
                        .inputs(objectMapper.convertValue(stored.getInputs(), new TypeReference<Map<String, Object>>() {
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
                .complexityPinned(d.isComplexityPinned())
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
            PlayValues values = playValues(entry.getValue().get(current.userId()));
            scenarios.put(entry.getKey(), values);
            if (entry.getKey() != ComplexityScenario.CURRENT) {
                deltas.put(entry.getKey(), playDelta(values, current));
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
