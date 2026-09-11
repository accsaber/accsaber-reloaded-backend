package com.accsaber.backend.service.map;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.dto.projection.ActiveComplexityRow;
import com.accsaber.backend.model.dto.projection.SimulationScoreRow;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.map.MapDifficultyComplexityEstimateRepository;
import com.accsaber.backend.repository.map.MapDifficultyComplexityRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.service.score.APCalculationService;
import com.accsaber.backend.util.Rounding;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ComplexityScenarioService {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final int AP_SCALE = 6;

    private final ScoreRepository scoreRepository;
    private final CategoryRepository categoryRepository;
    private final MapDifficultyComplexityRepository complexityRepository;
    private final MapDifficultyComplexityEstimateRepository estimateRepository;
    private final APCalculationService apCalculationService;

    private static final int EASE_ITERATIONS = 50;

    private static final int PREVIEW_CACHE_SIZE = 8;

    private final Map<ComplexityScenario, Cached<ScenarioState>> states = new ConcurrentHashMap<>();
    private final Map<String, Cached<ScenarioState>> previews = new ConcurrentHashMap<>();
    private volatile Cached<Pool> pool;
    private final Map<String, Cached<Map<UUID, BoardEase>>> boardEase = new ConcurrentHashMap<>();

    public record Play(Long userId, UUID difficultyId, UUID categoryId, double accuracy, double ap,
            double weightedAp, int position, int rank) {
    }

    public record PlayerTotal(double ap, int rank) {
    }

    public record MapAggregate(Double complexity, int scores, double topAp, double averageWeightedAp) {
    }

    public record Ladder(int players, double totalAp, int playersWith900, int playersWith1000,
            int playersWith1100, int playsWith1000, int playsWith1100, double topPlayAp) {
    }

    public record BoardEase(double ease, int players, int scores) {
    }

    private record Observation(Long userId, UUID mapDifficultyId, double linearised) {
    }


    public record ScenarioState(Map<UUID, Double> complexities, Map<UUID, List<Play>> playsByDifficulty,
            Map<Long, List<Play>> playsByUser, Map<UUID, Map<Long, PlayerTotal>> totalsByCategory,
            Map<Long, PlayerTotal> overallTotals, Map<UUID, MapAggregate> aggregates, Map<UUID, Ladder> ladders,
            Ladder overallLadder) {
    }

    private record Pool(List<SimulationScoreRow> rows, Map<UUID, Category> categories) {
    }

    private record Cached<T>(T value, Instant loadedAt) {
        boolean fresh() {
            return loadedAt.plus(TTL).isAfter(Instant.now());
        }
    }

    public void evict() {
        states.clear();
        previews.clear();
        pool = null;
        boardEase.clear();
    }

    public ScenarioState preview(String key, Supplier<Map<UUID, Double>> complexities) {
        Cached<ScenarioState> cached = previews.get(key);
        if (cached != null && cached.fresh()) {
            return cached.value();
        }
        ScenarioState state = evaluate(complexities.get());
        if (previews.size() >= PREVIEW_CACHE_SIZE) {
            previews.clear();
        }
        previews.put(key, new Cached<>(state, Instant.now()));
        return state;
    }

    public Map<UUID, BoardEase> boardEase(int minPlayerPlays, int topPlays) {
        String window = minPlayerPlays + ":" + topPlays;
        Cached<Map<UUID, BoardEase>> cached = boardEase.get(window);
        if (cached != null && cached.fresh()) {
            return cached.value();
        }
        Map<UUID, BoardEase> computed = computeBoardEase(loadPool(), minPlayerPlays, topPlays);
        boardEase.put(window, new Cached<>(computed, Instant.now()));
        return computed;
    }

    private static Map<UUID, BoardEase> computeBoardEase(Pool loaded, int minPlayerPlays, int topPlays) {
        Map<UUID, Integer> scores = new HashMap<>();
        Map<UUID, Map<Long, List<Observation>>> byCategory = new HashMap<>();
        for (SimulationScoreRow row : loaded.rows()) {
            if (row.maxScore() == null || row.maxScore() == 0 || row.score() == null || row.score() <= 0) {
                continue;
            }
            scores.merge(row.mapDifficultyId(), 1, Integer::sum);
            double accuracy = (double) row.score() / (double) row.maxScore();
            if (accuracy >= 1.0) {
                continue;
            }
            byCategory.computeIfAbsent(row.categoryId(), k -> new HashMap<>())
                    .computeIfAbsent(row.userId(), k -> new ArrayList<>())
                    .add(new Observation(row.userId(), row.mapDifficultyId(), -Math.log10(1.0 - accuracy)));
        }
        Map<UUID, BoardEase> result = new HashMap<>();
        for (Map<Long, List<Observation>> players : byCategory.values()) {
            result.putAll(fitEase(players, minPlayerPlays, topPlays, scores));
        }
        return result;
    }

    private static Map<UUID, BoardEase> fitEase(Map<Long, List<Observation>> players, int minPlayerPlays,
            int topPlays, Map<UUID, Integer> scores) {
        List<Observation> rows = players.values().stream()
                .filter(list -> list.size() >= minPlayerPlays)
                .flatMap(List::stream)
                .toList();
        Map<UUID, Integer> count = new HashMap<>();
        for (Observation row : rows) {
            count.merge(row.mapDifficultyId(), 1, Integer::sum);
        }
        Map<Long, Double> skill = new HashMap<>();
        Map<UUID, Double> ease = new HashMap<>();
        for (int it = 0; it < EASE_ITERATIONS; it++) {
            Map<Long, double[]> userSums = new HashMap<>();
            for (Observation row : rows) {
                double[] sum = userSums.computeIfAbsent(row.userId(), k -> new double[2]);
                sum[0] += row.linearised() - ease.getOrDefault(row.mapDifficultyId(), 0.0);
                sum[1]++;
            }
            userSums.forEach((user, sum) -> skill.put(user, sum[0] / sum[1]));
            Map<UUID, Double> mapSums = new HashMap<>();
            for (Observation row : rows) {
                mapSums.merge(row.mapDifficultyId(), row.linearised() - skill.get(row.userId()), Double::sum);
            }
            mapSums.forEach((map, sum) -> ease.put(map, sum / count.get(map)));
        }
        Map<UUID, List<Observation>> byMap = rows.stream().collect(Collectors.groupingBy(Observation::mapDifficultyId));
        Map<UUID, double[]> topSums = new HashMap<>();
        byMap.forEach((map, plays) -> {
            double[] sum = new double[2];
            plays.stream()
                    .sorted(Comparator.comparingDouble(Observation::linearised).reversed())
                    .limit(topPlays)
                    .forEach(row -> {
                        sum[0] += row.linearised() - skill.get(row.userId());
                        sum[1]++;
                    });
            topSums.put(map, sum);
        });
        double centre = topSums.values().stream()
                .filter(sum -> sum[1] >= topPlays)
                .mapToDouble(sum -> sum[0] / sum[1])
                .average().orElse(0.0);
        Map<UUID, BoardEase> result = new HashMap<>();
        topSums.forEach((map, sum) -> result.put(map,
                new BoardEase(sum[0] / sum[1] - centre, (int) sum[1], scores.getOrDefault(map, 0))));
        return result;
    }

    public ScenarioState state(ComplexityScenario scenario) {
        if (!scenario.stored()) {
            throw new IllegalArgumentException(scenario + " has no stored complexities");
        }
        Cached<ScenarioState> cached = states.get(scenario);
        if (cached != null) {
            return cached.value();
        }
        return build(scenario);
    }

    @Async("taskExecutor")
    public void rebuildAsync() {
        rebuild();
    }

    public void rebuild() {
        pool = null;
        boardEase.clear();
        previews.clear();
        for (ComplexityScenario scenario : ComplexityScenario.STORED) {
            build(scenario);
        }
    }

    private ScenarioState build(ComplexityScenario scenario) {
        ScenarioState state = evaluate(complexitiesFor(scenario));
        states.put(scenario, new Cached<>(state, Instant.now()));
        return state;
    }

    @Transactional(readOnly = true)
    public Map<UUID, Double> complexitiesFor(ComplexityScenario scenario) {
        Map<UUID, Double> current = complexityRepository
                .findActiveRowsByDifficultyStatus(MapDifficultyStatus.RANKED).stream()
                .collect(Collectors.toMap(ActiveComplexityRow::mapDifficultyId, ActiveComplexityRow::complexity,
                        (first, second) -> first));
        if (scenario == ComplexityScenario.CURRENT) {
            return current;
        }
        Map<UUID, Double> merged = new HashMap<>(current);
        for (ActiveComplexityRow row : estimateRepository.findRows()) {
            if (merged.containsKey(row.mapDifficultyId())) {
                merged.put(row.mapDifficultyId(), row.complexity());
            }
        }
        return merged;
    }

    @Transactional(readOnly = true)
    public ScenarioState evaluate(Map<UUID, Double> complexities) {
        Pool loaded = loadPool();
        Map<UUID, Map<Long, List<Play>>> byCategoryUser = new HashMap<>();
        for (SimulationScoreRow row : loaded.rows()) {
            Category category = loaded.categories().get(row.categoryId());
            Double complexity = complexities.get(row.mapDifficultyId());
            if (category == null || complexity == null || row.maxScore() == null || row.maxScore() == 0
                    || row.score() == null) {
                continue;
            }
            double accuracy = Rounding.round((double) row.score() / (double) row.maxScore(), 10);
            double ap = apCalculationService.calculateRawAP(accuracy, complexity, category.getScoreCurve()).rawAP();
            byCategoryUser.computeIfAbsent(category.getId(), k -> new HashMap<>())
                    .computeIfAbsent(row.userId(), k -> new ArrayList<>())
                    .add(new Play(row.userId(), row.mapDifficultyId(), category.getId(), accuracy, ap, 0.0, 0, 0));
        }

        Map<UUID, List<Play>> playsByDifficulty = new HashMap<>();
        Map<UUID, Map<Long, PlayerTotal>> totalsByCategory = new HashMap<>();
        Map<UUID, Ladder> ladders = new HashMap<>();
        Map<Long, Double> overallAp = new HashMap<>();
        for (var categoryEntry : byCategoryUser.entrySet()) {
            Category category = loaded.categories().get(categoryEntry.getKey());
            Map<Long, Double> totals = new HashMap<>();
            for (var userEntry : categoryEntry.getValue().entrySet()) {
                List<Play> sorted = userEntry.getValue().stream()
                        .sorted(Comparator.comparingDouble(Play::ap).reversed()).toList();
                double total = 0.0;
                for (int i = 0; i < sorted.size(); i++) {
                    Play play = sorted.get(i);
                    double weighted = apCalculationService.calculateWeightedAP(play.ap(), i, category.getWeightCurve());
                    total += weighted;
                    playsByDifficulty.computeIfAbsent(play.difficultyId(), k -> new ArrayList<>())
                            .add(new Play(play.userId(), play.difficultyId(), play.categoryId(), play.accuracy(),
                                    play.ap(), weighted, i + 1, 0));
                }
                totals.put(userEntry.getKey(), Rounding.round(total, AP_SCALE));
                if (category.isCountForOverall()) {
                    overallAp.merge(userEntry.getKey(), total, Double::sum);
                }
            }
            totalsByCategory.put(category.getId(), ranked(totals));
            ladders.put(category.getId(), ladder(categoryEntry.getValue().values(), totals));
        }

        Map<UUID, MapAggregate> aggregates = new HashMap<>();
        Map<Long, List<Play>> playsByUser = new HashMap<>();
        for (var entry : playsByDifficulty.entrySet()) {
            List<Play> sorted = entry.getValue().stream()
                    .sorted(Comparator.comparingDouble(Play::ap).reversed().thenComparing(Play::userId)).toList();
            List<Play> rankedPlays = new ArrayList<>(sorted.size());
            for (int i = 0; i < sorted.size(); i++) {
                Play play = sorted.get(i);
                Play ranked = new Play(play.userId(), play.difficultyId(), play.categoryId(), play.accuracy(),
                        play.ap(), play.weightedAp(), play.position(), i + 1);
                rankedPlays.add(ranked);
                playsByUser.computeIfAbsent(ranked.userId(), k -> new ArrayList<>()).add(ranked);
            }
            entry.setValue(rankedPlays);
            aggregates.put(entry.getKey(), new MapAggregate(complexities.get(entry.getKey()), rankedPlays.size(),
                    Rounding.round(rankedPlays.get(0).ap(), AP_SCALE),
                    Rounding.round(rankedPlays.stream().mapToDouble(Play::weightedAp).average().orElse(0.0),
                            AP_SCALE)));
        }

        Map<Long, Double> overallRounded = new HashMap<>();
        overallAp.forEach((userId, sum) -> overallRounded.put(userId, Rounding.round(sum, AP_SCALE)));
        Map<Long, PlayerTotal> overallTotals = ranked(overallRounded);
        List<List<Play>> overallPlays = byCategoryUser.entrySet().stream()
                .filter(e -> loaded.categories().get(e.getKey()).isCountForOverall())
                .flatMap(e -> e.getValue().values().stream())
                .toList();
        return new ScenarioState(complexities, playsByDifficulty, playsByUser, totalsByCategory, overallTotals,
                aggregates, ladders, ladder(overallPlays, overallRounded));
    }

    private Pool loadPool() {
        Cached<Pool> cached = pool;
        if (cached != null && cached.fresh()) {
            return cached.value();
        }
        Map<UUID, Category> categories = categoryRepository.findByActiveTrue().stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        Pool loaded = new Pool(scoreRepository.findActiveRowsByDifficultyStatus(MapDifficultyStatus.RANKED),
                categories);
        pool = new Cached<>(loaded, Instant.now());
        return loaded;
    }

    private static Map<Long, PlayerTotal> ranked(Map<Long, Double> totals) {
        List<Long> order = totals.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<Long, Double> e) -> e.getValue()).reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .toList();
        Map<Long, PlayerTotal> result = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            result.put(order.get(i), new PlayerTotal(totals.get(order.get(i)), i + 1));
        }
        return result;
    }

    private static Ladder ladder(Iterable<List<Play>> playsPerUser, Map<Long, Double> totals) {
        int players = 0;
        int players900 = 0;
        int players1000 = 0;
        int players1100 = 0;
        int plays1000 = 0;
        int plays1100 = 0;
        double top = 0.0;
        for (List<Play> plays : playsPerUser) {
            players++;
            double best = 0.0;
            for (Play play : plays) {
                best = Math.max(best, play.ap());
                if (play.ap() >= 1000) {
                    plays1000++;
                }
                if (play.ap() >= 1100) {
                    plays1100++;
                }
            }
            top = Math.max(top, best);
            if (best >= 900) {
                players900++;
            }
            if (best >= 1000) {
                players1000++;
            }
            if (best >= 1100) {
                players1100++;
            }
        }
        double totalAp = Rounding.round(totals.values().stream().mapToDouble(Double::doubleValue).sum(), AP_SCALE);
        return new Ladder(players, totalAp, players900, players1000, players1100, plays1000, plays1100,
                Rounding.round(top, AP_SCALE));
    }

    public Map<ComplexityScenario, ScenarioState> stored() {
        Map<ComplexityScenario, ScenarioState> result = new EnumMap<>(ComplexityScenario.class);
        for (ComplexityScenario scenario : ComplexityScenario.STORED) {
            result.put(scenario, state(scenario));
        }
        return result;
    }
}
