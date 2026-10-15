package com.accsaber.backend.service.score;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.map.MapDifficultyComplexityService;
import com.accsaber.backend.util.Rounding;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class XPReweightService {

    private static final Logger log = LoggerFactory.getLogger(XPReweightService.class);

    private final ScoreRepository scoreRepository;
    private final UserRepository userRepository;
    private final UserCategoryStatisticsRepository statisticsRepository;
    private final MapDifficultyComplexityService mapComplexityService;
    private final XPCalculationService xpCalculationService;
    @Qualifier("backfillExecutor")
    private final Executor backfillExecutor;

    @Async("taskExecutor")
    public CompletableFuture<Void> reweightAllScores() {
        log.info("Starting full XP reweight");
        xpCalculationService.evictXpCurveCache();

        List<UUID> difficultyIds = scoreRepository.findDistinctMapDifficultyIds();
        AtomicInteger updated = new AtomicInteger();

        List<CompletableFuture<Void>> futures = difficultyIds.stream()
                .map(difficultyId -> CompletableFuture.runAsync(() -> {
                    try {
                        updated.addAndGet(reweightScoresForDifficulty(difficultyId));
                    } catch (Exception e) {
                        log.error("XP reweight failed for difficulty {}", difficultyId, e);
                    }
                }, backfillExecutor))
                .toList();

        futures.forEach(CompletableFuture::join);

        log.info("XP reweight updated {} scores across {} difficulties. Rebuilding derived totals",
                updated.get(), difficultyIds.size());
        rebuildAllTotals();

        log.info("XP reweight complete");
        return CompletableFuture.completedFuture(null);
    }

    @Transactional
    public int reweightScoresForDifficulty(UUID difficultyId) {
        List<Score> allScores = scoreRepository.findAllByDifficultyOrderedByUserAndTime(difficultyId);
        if (allScores.isEmpty()) {
            return 0;
        }

        Score sample = allScores.get(0);
        if (sample.getMapDifficulty() == null
                || sample.getMapDifficulty().getCategory() == null
                || sample.getMapDifficulty().getCategory().getScoreCurve() == null
                || sample.getMapDifficulty().getMaxScore() == null
                || sample.getMapDifficulty().getMaxScore() == 0) {
            return 0;
        }

        Double complexity = mapComplexityService.findActiveComplexity(difficultyId)
                .orElse(1.0);
        int maxScore = sample.getMapDifficulty().getMaxScore();

        Map<Long, List<Score>> scoresByUser = new LinkedHashMap<>();
        for (Score score : allScores) {
            scoresByUser.computeIfAbsent(score.getUser().getId(), k -> new ArrayList<>()).add(score);
        }

        int updated = 0;
        for (List<Score> userScores : scoresByUser.values()) {
            Double currentBestAccuracy = null;
            for (Score score : userScores) {
                Double accuracy = Rounding.round((double) score.getScore() / maxScore, 10);
                Double newXp;
                if (score.isReweightDerivative()) {
                    newXp = 0.0;
                } else if (score.isPartial()) {
                    newXp = xpCalculationService.calculateXpForWorseScore();
                } else if (currentBestAccuracy == null) {
                    newXp = xpCalculationService.calculateXpForNewMap(accuracy, complexity);
                    currentBestAccuracy = accuracy;
                } else if ("Score improved".equals(score.getSupersedesReason())) {
                    newXp = xpCalculationService.calculateXpForImprovement(accuracy, currentBestAccuracy, complexity);
                    currentBestAccuracy = accuracy;
                } else {
                    newXp = xpCalculationService.calculateXpForWorseScore();
                }
                Double oldXp = score.getXpGained();
                if (oldXp == null || oldXp.compareTo(newXp) != 0) {
                    score.setXpGained(newXp);
                    updated++;
                }
            }
        }

        scoreRepository.saveAll(allScores);
        return updated;
    }

    @Async("taskExecutor")
    @Transactional
    public CompletableFuture<Void> recalculateTotalXpForAllUsers() {
        log.info("Starting bulk total XP recalculation for all users");
        rebuildAllTotals();
        log.info("Bulk total XP recalculation complete");
        return CompletableFuture.completedFuture(null);
    }

    private void rebuildAllTotals() {
        statisticsRepository.rebuildScoreXp(null);
        userRepository.rebuildXpTotals(null);
    }

    @Async("taskExecutor")
    @Transactional
    public CompletableFuture<Void> recalculateTotalXpForUser(Long userId) {
        log.info("Starting total XP recalculation for user {}", userId);
        statisticsRepository.rebuildScoreXp(userId);
        userRepository.rebuildXpTotals(userId);
        log.info("Total XP recalculation complete for user {}", userId);
        return CompletableFuture.completedFuture(null);
    }

}