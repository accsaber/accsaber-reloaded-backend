package com.accsaber.backend.service.score;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.dto.APResult;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.UserCategoryStatistics;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.map.MapDifficultyComplexityService;
import com.accsaber.backend.service.map.MapDifficultyStatisticsService;
import com.accsaber.backend.service.milestone.MilestoneEvaluationService;
import com.accsaber.backend.service.songsuggest.SongSuggestService;
import com.accsaber.backend.service.stats.OverallStatisticsService;
import com.accsaber.backend.service.stats.RankingService;
import com.accsaber.backend.service.stats.StatisticsService;
import com.accsaber.backend.util.Rounding;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreRecalculationService {

    private final ScoreRepository scoreRepository;
    private final ScoreService scoreService;
    private final StatisticsService statisticsService;
    private final OverallStatisticsService overallStatisticsService;
    private final RankingService rankingService;
    private final MapDifficultyStatisticsService mapDifficultyStatisticsService;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final MapDifficultyComplexityService mapComplexityService;
    private final APCalculationService apCalculationService;
    private final XPReweightService xpReweightService;
    private final MilestoneEvaluationService milestoneEvaluationService;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final UserCategoryStatisticsRepository userCategoryStatisticsRepository;
    private final com.accsaber.backend.service.skill.SkillService skillService;
    private final com.accsaber.backend.repository.user.UserCategorySkillRepository userCategorySkillRepository;
    private final ScoreRankingService scoreRankingService;
    private final SongSuggestService songSuggestService;

    @Autowired
    @Qualifier("backfillExecutor")
    private Executor backfillExecutor;

    @Async("taskExecutor")
    public CompletableFuture<Void> recalculateDifficultyAsync(UUID mapDifficultyId) {
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrueWithCategory(mapDifficultyId)
                .orElse(null);
        if (difficulty == null) {
            log.warn("Cannot recalculate: difficulty {} not found or inactive", mapDifficultyId);
            return CompletableFuture.completedFuture(null);
        }

        Set<Long> affected = recalculateScoreApsParallel(difficulty);

        if (affected.isEmpty()) {
            log.info("No AP changes for difficulty {}", difficulty.getId());
            return CompletableFuture.completedFuture(null);
        }

        UUID categoryId = difficulty.getCategory().getId();
        scoreRankingService.reassignRanks(difficulty.getId());
        batchRecalculateStats(affected, categoryId);
        mapDifficultyStatisticsService.recalculate(difficulty, null);
        rankingService.updateRankings(categoryId);
        if (difficulty.getCategory().isCountForOverall()) {
            overallStatisticsService.updateOverallRankings();
        }
        resweepSkills(Set.of(categoryId));
        try {
            xpReweightService.reweightScoresForDifficulty(difficulty.getId());
        } catch (Exception e) {
            log.error("XP reweight failed for difficulty {}: {}", difficulty.getId(), e.getMessage());
        }
        userRepository.recalculateTotalXpForAllActiveUsers();
        log.info("Recalculation complete for difficulty {} ({} users affected)", difficulty.getId(), affected.size());
        return CompletableFuture.completedFuture(null);
    }

    @Async("taskExecutor")
    public CompletableFuture<Void> recalculateBatchAsync(List<MapDifficulty> difficulties) {
        log.info("Starting batch recalculation for {} difficulties", difficulties.size());
        apCalculationService.evictAllCurveCaches();

        ConcurrentHashMap<UUID, Set<Long>> affectedByCategory = new ConcurrentHashMap<>();
        ConcurrentHashMap<UUID, Boolean> categoryIsOverall = new ConcurrentHashMap<>();

        List<CompletableFuture<Void>> futures = difficulties.stream()
                .map(difficulty -> CompletableFuture.runAsync(() -> {
                    try {
                        Set<Long> affected = recalculateRawApForDifficulty(difficulty);
                        if (!affected.isEmpty()) {
                            UUID categoryId = difficulty.getCategory().getId();
                            affectedByCategory.computeIfAbsent(
                                    categoryId, k -> ConcurrentHashMap.newKeySet())
                                    .addAll(affected);
                            categoryIsOverall.putIfAbsent(categoryId,
                                    difficulty.getCategory().isCountForOverall());
                            scoreRankingService.reassignRanks(difficulty.getId());
                            mapDifficultyStatisticsService.recalculate(difficulty, null);
                            xpReweightService.reweightScoresForDifficulty(difficulty.getId());
                        }
                    } catch (Exception e) {
                        log.error("Batch recalc failed for difficulty {}: {}", difficulty.getId(), e.getMessage());
                    }
                }, backfillExecutor))
                .toList();

        futures.forEach(CompletableFuture::join);

        coalescedStatsRecalc(affectedByCategory, categoryIsOverall);

        boolean anyChanges = affectedByCategory.values().stream().anyMatch(s -> !s.isEmpty());
        if (anyChanges) {
            userRepository.recalculateTotalXpForAllActiveUsers();
        }

        int totalUsers = affectedByCategory.values().stream().mapToInt(Set::size).sum();
        log.info("Batch recalculation complete for {} difficulties, {} users affected",
                difficulties.size(), totalUsers);

        songSuggestService.regenerateAsync();
        return CompletableFuture.completedFuture(null);
    }

    @Async("taskExecutor")
    public CompletableFuture<Void> recalculateAllRawApAsync() {
        doRecalculateAllRawAp();
        return CompletableFuture.completedFuture(null);
    }

    private void doRecalculateAllRawAp() {
        log.info("Starting raw AP recalculation for all ranked difficulties");
        apCalculationService.evictAllCurveCaches();

        List<MapDifficulty> difficulties = mapDifficultyRepository
                .findByStatusAndActiveTrueWithCategory(MapDifficultyStatus.RANKED);

        if (difficulties.isEmpty()) {
            log.info("No ranked difficulties found");
            return;
        }

        ConcurrentHashMap<UUID, Set<Long>> affectedByCategory = new ConcurrentHashMap<>();
        ConcurrentHashMap<UUID, Boolean> categoryIsOverall = new ConcurrentHashMap<>();

        List<CompletableFuture<Void>> futures = difficulties.stream()
                .map(difficulty -> CompletableFuture.runAsync(() -> {
                    try {
                        Set<Long> affected = recalculateRawApForDifficulty(difficulty);
                        if (!affected.isEmpty()) {
                            UUID categoryId = difficulty.getCategory().getId();
                            affectedByCategory.computeIfAbsent(
                                    categoryId, k -> ConcurrentHashMap.newKeySet())
                                    .addAll(affected);
                            categoryIsOverall.putIfAbsent(categoryId,
                                    difficulty.getCategory().isCountForOverall());
                            scoreRankingService.reassignRanks(difficulty.getId());
                            mapDifficultyStatisticsService.recalculate(difficulty, null);
                            xpReweightService.reweightScoresForDifficulty(difficulty.getId());
                        }
                    } catch (Exception e) {
                        log.error("Raw AP recalc failed for difficulty {}: {}", difficulty.getId(), e.getMessage());
                    }
                }, backfillExecutor))
                .toList();

        futures.forEach(CompletableFuture::join);

        coalescedStatsRecalc(affectedByCategory, categoryIsOverall);

        userRepository.recalculateTotalXpForAllActiveUsers();

        int totalUsers = affectedByCategory.values().stream().mapToInt(Set::size).sum();
        log.info("Raw AP recalculation complete for {} difficulties, {} users affected",
                difficulties.size(), totalUsers);
    }

    @Transactional
    public Set<Long> recalculateRawApForDifficulty(MapDifficulty difficulty) {
        List<Score> scores = scoreRepository.findByMapDifficultyIdAndActiveTrueWithCategory(difficulty.getId());
        if (scores.isEmpty())
            return Set.of();

        if (difficulty.getMaxScore() == null || difficulty.getMaxScore() == 0)
            return Set.of();

        Double complexity = mapComplexityService.findActiveComplexity(difficulty.getId()).orElse(null);
        if (complexity == null) {
            log.warn("No active complexity for difficulty {} - skipping", difficulty.getId());
            return Set.of();
        }

        Curve scoreCurve = difficulty.getCategory().getScoreCurve();
        int maxScore = difficulty.getMaxScore();
        Set<Long> affectedUsers = new HashSet<>();

        for (Score score : scores) {
            Double accuracy = Rounding.round((double) (score.getScore()) / (double) (maxScore), 10);
            APResult apResult = apCalculationService.calculateRawAP(accuracy, complexity, scoreCurve);
            if ((apResult.rawAP() != score.getAp())) {
                score.setAp(apResult.rawAP());
                affectedUsers.add(score.getUser().getId());
            }
        }

        scoreRepository.saveAll(scores);
        return affectedUsers;
    }

    @Async("taskExecutor")
    public CompletableFuture<Void> recalculateAllWeightedApAsync() {
        doRecalculateAllWeightedAp();
        return CompletableFuture.completedFuture(null);
    }

    private void doRecalculateAllWeightedAp() {
        log.info("Starting weighted AP recalculation for all categories");
        List<UUID> categoryIds = categoryRepository.findByActiveTrue().stream()
                .filter(c -> !"overall".equals(c.getCode()))
                .map(c -> c.getId())
                .toList();

        for (UUID categoryId : categoryIds) {
            List<UserCategoryStatistics> stats = userCategoryStatisticsRepository
                    .findActiveByCategoryOrderByApDesc(categoryId);
            Set<Long> userIds = ConcurrentHashMap.newKeySet();
            stats.forEach(s -> userIds.add(s.getUser().getId()));

            if (userIds.isEmpty()) {
                log.info("No users with stats in category {}", categoryId);
                continue;
            }

            batchRecalculateStats(userIds, categoryId);
            rankingService.updateRankings(categoryId);
            log.info("Weighted AP recalculation complete for category {} ({} users)", categoryId, userIds.size());
        }
        overallStatisticsService.updateOverallRankings();
        log.info("Weighted AP recalculation complete for all categories");
    }

    @Async("taskExecutor")
    public CompletableFuture<Void> recalculateAllApAsync() {
        log.info("Starting full AP recalculation (raw + weighted)");
        doRecalculateAllRawAp();
        doRecalculateAllWeightedAp();
        log.info("Full AP recalculation complete");
        return CompletableFuture.completedFuture(null);
    }

    private Set<Long> recalculateScoreApsParallel(MapDifficulty difficulty) {
        Double complexity = mapComplexityService.findActiveComplexity(difficulty.getId()).orElse(null);
        if (complexity == null) {
            log.warn("No active complexity for difficulty {} - skipping", difficulty.getId());
            return ConcurrentHashMap.newKeySet();
        }

        List<UUID> scoreIds = scoreRepository.findActiveIdsByMapDifficultyId(difficulty.getId());
        Set<Long> affected = ConcurrentHashMap.newKeySet();

        List<CompletableFuture<Void>> futures = scoreIds.stream()
                .map(scoreId -> CompletableFuture.runAsync(() -> {
                    try {
                        ScoreService.RecalcResult result = scoreService.recalculateScoreForBatch(scoreId,
                                difficulty, complexity);
                        if (result != null)
                            affected.add(result.userId());
                    } catch (Exception e) {
                        log.error("AP recalc failed for score {}: {}", scoreId, e.getMessage());
                    }
                }, backfillExecutor))
                .toList();

        futures.forEach(CompletableFuture::join);
        return affected;
    }

    private void coalescedStatsRecalc(java.util.Map<UUID, Set<Long>> affectedByCategory,
            java.util.Map<UUID, Boolean> categoryIsOverall) {
        if (affectedByCategory.isEmpty()) {
            return;
        }

        Set<Long> allUsers = new HashSet<>();
        Set<Long> overallUsers = new HashSet<>();
        for (var e : affectedByCategory.entrySet()) {
            allUsers.addAll(e.getValue());
            if (Boolean.TRUE.equals(categoryIsOverall.get(e.getKey()))) {
                overallUsers.addAll(e.getValue());
            }
        }

        List<CompletableFuture<Void>> statsFutures = new java.util.ArrayList<>();
        for (var e : affectedByCategory.entrySet()) {
            UUID categoryId = e.getKey();
            for (Long userId : e.getValue()) {
                statsFutures.add(CompletableFuture.runAsync(() -> {
                    try {
                        statisticsService.recalculate(userId, categoryId, false, false);
                    } catch (Exception ex) {
                        log.error("Stats recalc failed for user {} category {}: {}",
                                userId, categoryId, ex.getMessage());
                    }
                }, backfillExecutor));
            }
        }
        statsFutures.forEach(CompletableFuture::join);

        List<CompletableFuture<Void>> userFutures = allUsers.stream()
                .map(userId -> CompletableFuture.runAsync(() -> {
                    try {
                        if (overallUsers.contains(userId)) {
                            overallStatisticsService.recalculate(userId, false);
                        }
                        milestoneEvaluationService.evaluateAllForUser(userId);
                    } catch (Exception ex) {
                        log.error("Per-user post-recalc failed for user {}: {}",
                                userId, ex.getMessage());
                    }
                }, backfillExecutor))
                .toList();
        userFutures.forEach(CompletableFuture::join);

        for (UUID categoryId : affectedByCategory.keySet()) {
            try {
                rankingService.updateRankings(categoryId);
            } catch (Exception ex) {
                log.error("Category ranking update failed for {}: {}", categoryId, ex.getMessage());
            }
        }

        boolean anyOverall = overallUsers.size() > 0
                || categoryIsOverall.values().stream().anyMatch(Boolean.TRUE::equals);
        if (anyOverall) {
            try {
                overallStatisticsService.updateOverallRankings();
            } catch (Exception ex) {
                log.error("Overall ranking update failed: {}", ex.getMessage());
            }
        }
        resweepSkills(affectedByCategory.keySet());
    }

    private void resweepSkills(Set<UUID> categoryIds) {
        for (UUID categoryId : categoryIds) {
            try {
                List<Long> engaged = userCategorySkillRepository.findActiveUserIdsByCategoryId(categoryId);
                skillService.recomputeCategorySkills(categoryId, engaged);
                log.info("Recomputed skills for {} engaged players in category {} after AP changes", engaged.size(),
                        categoryId);
            } catch (Exception ex) {
                log.error("Skill resweep failed for category {}: {}", categoryId, ex.getMessage());
            }
        }
    }

    private void batchRecalculateStats(Set<Long> userIds, UUID categoryId) {
        List<CompletableFuture<Void>> futures = userIds.stream()
                .map(userId -> CompletableFuture.runAsync(() -> {
                    try {
                        statisticsService.recalculate(userId, categoryId, false);
                        milestoneEvaluationService.evaluateAllForUser(userId);
                    } catch (Exception e) {
                        log.error("Stats recalc failed for user {}: {}", userId, e.getMessage());
                    }
                }, backfillExecutor))
                .toList();

        futures.forEach(CompletableFuture::join);
    }
}
