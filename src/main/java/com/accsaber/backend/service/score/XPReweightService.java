package com.accsaber.backend.service.score;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @Async("taskExecutor")
    @Transactional
    public void reweightAllScores() {
        log.info("Starting full XP reweight");
        xpCalculationService.evictXpCurveCache();

        List<UUID> difficultyIds = scoreRepository.findDistinctMapDifficultyIds();
        int updated = 0;

        for (UUID difficultyId : difficultyIds) {
            updated += reweightScoresForDifficulty(difficultyId);
        }

        recalculateTotalXpForAllUsers();
        log.info("XP reweight complete. Updated {} scores across {} difficulties", updated, difficultyIds.size());
    }

    private int reweightScoresForDifficulty(UUID difficultyId) {
        List<Score> scores = scoreRepository.findByMapDifficulty_Id(difficultyId);
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

        Map<Long, List<Score>> byUser = scores.stream()
                .filter(s -> s.getUser() != null)
                .collect(Collectors.groupingBy(s -> s.getUser().getId()));

        int updated = 0;
        for (List<Score> userScores : byUser.values()) {
            userScores.sort(
                    Comparator.comparing(Score::getTimeSet, Comparator.nullsFirst(Comparator.naturalOrder())));
            BigDecimal prevCurveBonus = BigDecimal.ZERO;

            for (Score score : userScores) {
                BigDecimal xpGained;
                if (!score.isActive() && score.getSupersedes() == null) {
                    xpGained = xpCalculationService.calculateXpForWorseScore();
                } else {
                    BigDecimal accuracy = computeAccuracy(score);
                    BigDecimal curveBonus = xpCalculationService.computeCurveBonus(accuracy, complexity);
                    BigDecimal delta = curveBonus.subtract(prevCurveBonus).max(BigDecimal.ZERO);
                    xpGained = BigDecimal.valueOf(xpCalculationService.getBaseXpPerScore()).add(delta);
                    prevCurveBonus = curveBonus.max(prevCurveBonus);
                }
                score.setXpGained(xpGained);
                updated++;
            }
        }

        scoreRepository.saveAll(scores);
        return updated;
    }

    private void recalculateTotalXpForAllUsers() {
        List<User> users = userRepository.findByActiveTrue();
        for (User user : users) {
            BigDecimal scoreXp = scoreRepository.sumXpGainedByUserId(user.getId());
            BigDecimal milestoneXp = userMilestoneLinkRepository.sumCompletedMilestoneXpByUserId(user.getId());
            BigDecimal setBonusXp = userMilestoneSetBonusRepository.sumSetBonusXpByUserId(user.getId());
            user.setTotalXp(scoreXp.add(milestoneXp).add(setBonusXp));
        }
        userRepository.saveAll(users);
    }

    private BigDecimal computeAccuracy(Score score) {
        return BigDecimal.valueOf(score.getScore())
                .divide(BigDecimal.valueOf(score.getMapDifficulty().getMaxScore()), 10, RoundingMode.HALF_UP);
    }
}
