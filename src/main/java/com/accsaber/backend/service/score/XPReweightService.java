package com.accsaber.backend.service.score;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.milestone.UserMilestoneLinkRepository;
import com.accsaber.backend.repository.milestone.UserMilestoneSetBonusRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.map.MapDifficultyComplexityService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class XPReweightService {

    private static final Logger log = LoggerFactory.getLogger(XPReweightService.class);

    private final ScoreRepository scoreRepository;
    private final UserRepository userRepository;
    private final UserMilestoneLinkRepository userMilestoneLinkRepository;
    private final UserMilestoneSetBonusRepository userMilestoneSetBonusRepository;
    private final MapDifficultyComplexityService mapComplexityService;
    private final XPCalculationService xpCalculationService;

    @Autowired
    @Qualifier("backfillExecutor")
    private Executor backfillExecutor;

    @Async("taskExecutor")
    public void reweightAllScores() {
        log.info("Starting full XP reweight");
        xpCalculationService.evictXpCurveCache();

        List<UUID> difficultyIds = scoreRepository.findDistinctMapDifficultyIds();
        int updated = 0;

        for (UUID difficultyId : difficultyIds) {
            updated += reweightScoresForDifficulty(difficultyId);
        }

        doRecalculateTotalXpForAllUsers();
        log.info("XP reweight complete. Updated {} scores across {} difficulties", updated, difficultyIds.size());
    }

    private int reweightScoresForDifficulty(UUID difficultyId) {
        List<Score> scores = scoreRepository.findByMapDifficultyIdAndActiveTrueWithCategory(difficultyId);
        if (scores.isEmpty()) {
            return 0;
        }

        Score sample = scores.get(0);
        if (sample.getMapDifficulty() == null
                || sample.getMapDifficulty().getCategory() == null
                || sample.getMapDifficulty().getCategory().getScoreCurve() == null
                || sample.getMapDifficulty().getMaxScore() == null
                || sample.getMapDifficulty().getMaxScore() == 0) {
            return 0;
        }

        BigDecimal complexity = mapComplexityService.findActiveComplexity(difficultyId)
                .orElse(BigDecimal.ONE);

        int maxScore = sample.getMapDifficulty().getMaxScore();

        List<CompletableFuture<Void>> futures = scores.stream()
                .map(score -> CompletableFuture.runAsync(() -> {
                    try {
                        reweightSingleScore(score.getId(), maxScore, complexity);
                    } catch (Exception e) {
                        log.error("XP reweight failed for score {}", score.getId(), e);
                    }
                }, backfillExecutor))
                .toList();

        futures.forEach(CompletableFuture::join);
        return scores.size();
    }

    @Transactional
    public void reweightSingleScore(UUID scoreId, int maxScore, BigDecimal complexity) {
        Score managed = scoreRepository.findById(scoreId).orElse(null);
        if (managed == null || !managed.isActive())
            return;

        BigDecimal accuracy = BigDecimal.valueOf(managed.getScore())
                .divide(BigDecimal.valueOf(maxScore), 10, RoundingMode.HALF_UP);
        BigDecimal xpGained = xpCalculationService.calculateXpForNewMap(accuracy, complexity);
        managed.setXpGained(xpGained);
        scoreRepository.save(managed);
    }

    @Async("taskExecutor")
    public void recalculateTotalXpForAllUsers() {
        doRecalculateTotalXpForAllUsers();
    }

    private void doRecalculateTotalXpForAllUsers() {
        log.info("Starting total XP recalculation for all users");
        List<User> users = userRepository.findByActiveTrue();

        List<CompletableFuture<Void>> futures = users.stream()
                .map(user -> CompletableFuture.runAsync(() -> {
                    try {
                        recalculateSingleUserXp(user.getId());
                    } catch (Exception e) {
                        log.error("XP recalc failed for user {}", user.getId(), e);
                    }
                }, backfillExecutor))
                .toList();

        futures.forEach(CompletableFuture::join);
        log.info("Total XP recalculation complete for {} users", users.size());
    }

    @Transactional
    public void recalculateSingleUserXp(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isActive())
            return;

        BigDecimal scoreXp = scoreRepository.sumXpGainedByUserId(userId);
        BigDecimal milestoneXp = userMilestoneLinkRepository.sumCompletedMilestoneXpByUserId(userId);
        BigDecimal setBonusXp = userMilestoneSetBonusRepository.sumSetBonusXpByUserId(userId);
        user.setTotalXp(scoreXp.add(milestoneXp).add(setBonusXp));
        userRepository.save(user);
    }

}