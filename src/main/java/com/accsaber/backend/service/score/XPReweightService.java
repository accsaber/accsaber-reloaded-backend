package com.accsaber.backend.service.score;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.entity.score.Score;
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

        log.info("XP reweight complete. Updated {} scores across {} difficulties. Run /total-xp to update user totals.",
                updated.get(), difficultyIds.size());
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

        BigDecimal complexity = mapComplexityService.findActiveComplexity(difficultyId)
                .orElse(BigDecimal.ONE);
        int maxScore = sample.getMapDifficulty().getMaxScore();

        Map<Long, List<Score>> scoresByUser = new LinkedHashMap<>();
        for (Score score : allScores) {
            scoresByUser.computeIfAbsent(score.getUser().getId(), k -> new ArrayList<>()).add(score);
        }

        int updated = 0;
        for (List<Score> userScores : scoresByUser.values()) {
            BigDecimal currentBestAccuracy = null;
            for (Score score : userScores) {
                BigDecimal accuracy = BigDecimal.valueOf(score.getScore())
                        .divide(BigDecimal.valueOf(maxScore), 10, RoundingMode.HALF_UP);
                BigDecimal newXp;
                if (currentBestAccuracy == null) {
                    newXp = xpCalculationService.calculateXpForNewMap(accuracy, complexity);
                    currentBestAccuracy = accuracy;
                } else if ("Score improved".equals(score.getSupersedesReason())) {
                    newXp = xpCalculationService.calculateXpForImprovement(accuracy, currentBestAccuracy, complexity);
                    currentBestAccuracy = accuracy;
                } else {
                    newXp = xpCalculationService.calculateXpForWorseScore();
                }
                BigDecimal oldXp = score.getXpGained();
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
    public void recalculateTotalXpForAllUsers() {
        log.info("Starting bulk total XP recalculation for all users");
        userRepository.recalculateTotalXpForAllActiveUsers();
        log.info("Bulk total XP recalculation complete");
    }

}