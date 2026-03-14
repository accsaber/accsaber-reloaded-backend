package com.accsaber.backend.service.score;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.milestone.Milestone;
import com.accsaber.backend.model.entity.milestone.MilestoneSet;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.UserCategoryStatistics;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.infra.MetricsService;
import com.accsaber.backend.service.map.MapDifficultyComplexityService;
import com.accsaber.backend.service.map.MapDifficultyStatisticsService;
import com.accsaber.backend.service.milestone.MilestoneEvaluationService;
import com.accsaber.backend.service.stats.OverallStatisticsService;
import com.accsaber.backend.service.stats.RankingService;
import com.accsaber.backend.service.stats.StatisticsService;

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
    private final XPCalculationService xpCalculationService;
    private final MilestoneEvaluationService milestoneEvaluationService;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final UserCategoryStatisticsRepository userCategoryStatisticsRepository;
    private final MetricsService metricsService;

    @Autowired
    @Qualifier("backfillExecutor")
    private Executor backfillExecutor;

    @Async("taskExecutor")
    public void recalculateScoresAsync(UUID mapDifficultyId) {
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrueWithCategory(mapDifficultyId)
                .orElse(null);
        if (difficulty == null) {
            log.warn("Cannot recalculate: difficulty {} not found or inactive", mapDifficultyId);
            return;
        }
        batchRecalculateScoresForDifficulty(difficulty, true);
    }

    @Async("taskExecutor")
    public void recalculateAllScoresForCategoryAsync(UUID categoryId) {
        metricsService.getApRecalculationTimer().record(() -> doRecalculateAllScoresForCategory(categoryId));
    }

    private void doRecalculateAllScoresForCategory(UUID categoryId) {
        apCalculationService.evictAllCurveCaches();

        List<MapDifficulty> difficulties = mapDifficultyRepository
                .findByCategoryIdAndStatusAndActiveTrueWithCategory(categoryId, MapDifficultyStatus.RANKED);

        if (difficulties.isEmpty()) {
            log.info("No ranked difficulties found for category {}", categoryId);
            return;
        }

        Set<Long> allAffectedUsers = ConcurrentHashMap.newKeySet();

        for (MapDifficulty difficulty : difficulties) {
            Set<Long> affected = recalculateScoreAps(difficulty);
            allAffectedUsers.addAll(affected);
            mapDifficultyStatisticsService.recalculate(difficulty, null);
        }

        if (allAffectedUsers.isEmpty()) {
            log.info("No AP changes for category {}", categoryId);
            return;
        }

        batchRecalculateStats(allAffectedUsers, categoryId);
        rankingService.updateRankings(categoryId);
        if (difficulties.get(0).getCategory().isCountForOverall()) {
            overallStatisticsService.updateOverallRankings();
        }
        log.info("Category {} AP recalculation complete: {} users affected", categoryId, allAffectedUsers.size());
    }

    @Async("taskExecutor")
    public void recalculateAllXpAsync() {
        xpCalculationService.evictXpCurveCache();

        List<MapDifficulty> allRanked = mapDifficultyRepository
                .findByStatusAndActiveTrue(MapDifficultyStatus.RANKED);

        for (MapDifficulty difficulty : allRanked) {
            BigDecimal complexity = mapComplexityService.findActiveComplexity(difficulty.getId()).orElse(null);
            if (complexity == null) {
                log.warn("No active complexity for difficulty {} - skipping XP recalc", difficulty.getId());
                continue;
            }

            List<Score> scores = scoreRepository.findByMapDifficulty_IdAndActiveTrue(difficulty.getId());

            List<CompletableFuture<Void>> futures = scores.stream()
                    .map(score -> CompletableFuture.runAsync(() -> {
                        try {
                            scoreService.recalculateScoreXpForBatch(score.getId(), difficulty, complexity);
                        } catch (Exception e) {
                            log.error("XP recalc failed for score {}: {}", score.getId(), e.getMessage());
                        }
                    }, backfillExecutor))
                    .toList();

            futures.forEach(CompletableFuture::join);
        }
        log.info("XP recalculation complete for all ranked difficulties");
    }

    @Async("taskExecutor")
    public void recalculateWeightCurveAsync(UUID curveId) {
        apCalculationService.evictCurveCache(curveId);

        List<Category> categories = categoryRepository.findByWeightCurve_IdAndActiveTrue(curveId);
        if (categories.isEmpty()) {
            log.info("No active categories use weight curve {}", curveId);
            return;
        }

        for (Category category : categories) {
            UUID categoryId = category.getId();
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
            if (category.isCountForOverall()) {
                overallStatisticsService.updateOverallRankings();
            }
            log.info("Weight curve recalculation complete for category {} ({} users)", categoryId, userIds.size());
        }
    }

    private void batchRecalculateScoresForDifficulty(MapDifficulty difficulty, boolean triggerMapStats) {
        Set<Long> affected = recalculateScoreAps(difficulty);

        if (affected.isEmpty()) {
            log.info("No AP changes for difficulty {}", difficulty.getId());
            return;
        }

        UUID categoryId = difficulty.getCategory().getId();
        batchRecalculateStats(affected, categoryId);

        if (triggerMapStats) {
            mapDifficultyStatisticsService.recalculate(difficulty, null);
        }
        rankingService.updateRankings(categoryId);
        if (difficulty.getCategory().isCountForOverall()) {
            overallStatisticsService.updateOverallRankings();
        }
        log.info("Recalculation complete for difficulty {} ({} users affected)", difficulty.getId(), affected.size());
    }

    private Set<Long> recalculateScoreAps(MapDifficulty difficulty) {
        BigDecimal complexity = mapComplexityService.findActiveComplexity(difficulty.getId()).orElse(null);
        if (complexity == null) {
            log.warn("No active complexity for difficulty {} - skipping", difficulty.getId());
            return ConcurrentHashMap.newKeySet();
        }

        List<Score> scores = scoreRepository.findByMapDifficulty_IdAndActiveTrue(difficulty.getId());
        Set<Long> affected = ConcurrentHashMap.newKeySet();

        List<CompletableFuture<Void>> futures = scores.stream()
                .map(score -> CompletableFuture.runAsync(() -> {
                    try {
                        ScoreService.RecalcResult result = scoreService.recalculateScoreForBatch(score.getId(),
                                difficulty, complexity);
                        if (result != null)
                            affected.add(result.userId());
                    } catch (Exception e) {
                        log.error("AP recalc failed for score {}: {}", score.getId(), e.getMessage());
                    }
                }, backfillExecutor))
                .toList();

        futures.forEach(CompletableFuture::join);
        return affected;
    }

    private void batchRecalculateStats(Set<Long> userIds, UUID categoryId) {
        List<CompletableFuture<Void>> futures = userIds.stream()
                .map(userId -> CompletableFuture.runAsync(() -> {
                    try {
                        statisticsService.recalculate(userId, categoryId, false);
                        var evaluation = milestoneEvaluationService.evaluateAllForUser(userId);
                        awardMilestoneXp(userId, evaluation);
                    } catch (Exception e) {
                        log.error("Stats recalc failed for user {}: {}", userId, e.getMessage());
                    }
                }, backfillExecutor))
                .toList();

        futures.forEach(CompletableFuture::join);
    }

    private void awardMilestoneXp(Long userId, MilestoneEvaluationService.EvaluationResult evaluation) {
        if (evaluation.completedMilestones().isEmpty() && evaluation.completedSets().isEmpty())
            return;

        BigDecimal milestoneXp = evaluation.completedMilestones().stream()
                .map(Milestone::getXp)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal setXp = evaluation.completedSets().stream()
                .map(MilestoneSet::getSetBonusXp)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal total = milestoneXp.add(setXp);
        if (total.compareTo(BigDecimal.ZERO) > 0) {
            userRepository.addXp(userId, total);
        }
    }
}
