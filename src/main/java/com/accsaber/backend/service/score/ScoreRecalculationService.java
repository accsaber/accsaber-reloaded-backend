package com.accsaber.backend.service.score;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
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
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.dto.APResult;
import com.accsaber.backend.model.entity.Curve;
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
    private final MilestoneEvaluationService milestoneEvaluationService;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final UserCategoryStatisticsRepository userCategoryStatisticsRepository;
    private final ScoreRankingService scoreRankingService;

    @Autowired
    @Qualifier("backfillExecutor")
    private Executor backfillExecutor;

@Async("taskExecutor")
    public void recalculateDifficultyAsync(UUID mapDifficultyId) {
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrueWithCategory(mapDifficultyId)
                .orElse(null);
        if (difficulty == null) {
            log.warn("Cannot recalculate: difficulty {} not found or inactive", mapDifficultyId);
            return;
        }

        Set<Long> affected = recalculateScoreApsParallel(difficulty);

        if (affected.isEmpty()) {
            log.info("No AP changes for difficulty {}", difficulty.getId());
            return;
        }

        UUID categoryId = difficulty.getCategory().getId();
        scoreRankingService.reassignRanks(difficulty.getId());
        batchRecalculateStats(affected, categoryId);
        mapDifficultyStatisticsService.recalculate(difficulty, null);
        rankingService.updateRankings(categoryId);
        if (difficulty.getCategory().isCountForOverall()) {
            overallStatisticsService.updateOverallRankings();
        }
        log.info("Recalculation complete for difficulty {} ({} users affected)", difficulty.getId(), affected.size());
    }

@Async("taskExecutor")
    public void recalculateAllRawApAsync() {
        doRecalculateAllRawAp();
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

        List<CompletableFuture<Void>> futures = difficulties.stream()
                .map(difficulty -> CompletableFuture.runAsync(() -> {
                    try {
                        Set<Long> affected = recalculateRawApForDifficulty(difficulty);
                        if (!affected.isEmpty()) {
                            affectedByCategory.computeIfAbsent(
                                    difficulty.getCategory().getId(), k -> ConcurrentHashMap.newKeySet()
                            ).addAll(affected);
                            scoreRankingService.reassignRanks(difficulty.getId());
                            mapDifficultyStatisticsService.recalculate(difficulty, null);
                        }
                    } catch (Exception e) {
                        log.error("Raw AP recalc failed for difficulty {}: {}", difficulty.getId(), e.getMessage());
                    }
                }, backfillExecutor))
                .toList();

        futures.forEach(CompletableFuture::join);

        for (var entry : affectedByCategory.entrySet()) {
            batchRecalculateStats(entry.getValue(), entry.getKey());
            rankingService.updateRankings(entry.getKey());
        }
        overallStatisticsService.updateOverallRankings();

        int totalUsers = affectedByCategory.values().stream().mapToInt(Set::size).sum();
        log.info("Raw AP recalculation complete for {} difficulties, {} users affected",
                difficulties.size(), totalUsers);
    }

    @Transactional
    public Set<Long> recalculateRawApForDifficulty(MapDifficulty difficulty) {
        List<Score> scores = scoreRepository.findByMapDifficultyIdAndActiveTrueWithCategory(difficulty.getId());
        if (scores.isEmpty()) return Set.of();

        if (difficulty.getMaxScore() == null || difficulty.getMaxScore() == 0) return Set.of();

        BigDecimal complexity = mapComplexityService.findActiveComplexity(difficulty.getId()).orElse(null);
        if (complexity == null) {
            log.warn("No active complexity for difficulty {} - skipping", difficulty.getId());
            return Set.of();
        }

        Curve scoreCurve = difficulty.getCategory().getScoreCurve();
        int maxScore = difficulty.getMaxScore();
        Set<Long> affectedUsers = new HashSet<>();

        for (Score score : scores) {
            BigDecimal accuracy = BigDecimal.valueOf(score.getScore())
                    .divide(BigDecimal.valueOf(maxScore), 10, RoundingMode.HALF_UP);
            APResult apResult = apCalculationService.calculateRawAP(accuracy, complexity, scoreCurve);
            if (apResult.rawAP().compareTo(score.getAp()) != 0) {
                score.setAp(apResult.rawAP());
                affectedUsers.add(score.getUser().getId());
            }
        }

        scoreRepository.saveAll(scores);
        return affectedUsers;
    }

@Async("taskExecutor")
    public void recalculateAllWeightedApAsync() {
        doRecalculateAllWeightedAp();
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
    public void recalculateAllApAsync() {
        log.info("Starting full AP recalculation (raw + weighted)");
        doRecalculateAllRawAp();
        doRecalculateAllWeightedAp();
        log.info("Full AP recalculation complete");
    }

    private Set<Long> recalculateScoreApsParallel(MapDifficulty difficulty) {
        BigDecimal complexity = mapComplexityService.findActiveComplexity(difficulty.getId()).orElse(null);
        if (complexity == null) {
            log.warn("No active complexity for difficulty {} - skipping", difficulty.getId());
            return ConcurrentHashMap.newKeySet();
        }

        List<Score> scores = scoreRepository.findByMapDifficultyIdAndActiveTrueWithUserAndCategory(difficulty.getId());
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
