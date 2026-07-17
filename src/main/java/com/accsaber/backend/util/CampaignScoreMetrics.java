package com.accsaber.backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.accsaber.backend.model.dto.projection.UserMapDifficultyBests;
import com.accsaber.backend.model.entity.campaign.BarrierConditionType;
import com.accsaber.backend.model.entity.campaign.CampaignRequirementType;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.score.Score;

public final class CampaignScoreMetrics {

    private CampaignScoreMetrics() {
    }

    public static BigDecimal accuracy(Score score) {
        MapDifficulty md = score.getMapDifficulty();
        if (md == null || md.getMaxScore() == null || md.getMaxScore() == 0 || score.getScoreNoMods() == null) {
            return null;
        }
        return BigDecimal.valueOf(score.getScoreNoMods())
                .divide(BigDecimal.valueOf(md.getMaxScore()), 6, RoundingMode.HALF_UP);
    }

    public static boolean isFullCombo(Score score) {
        return score.getMisses() != null && score.getBadCuts() != null
                && score.getMisses() == 0 && score.getBadCuts() == 0;
    }

    public static BigDecimal requirementValue(Score score, CampaignRequirementType type, Set<UUID> nfScoreIds) {
        return switch (type) {
            case ACC -> accuracy(score);
            case AP -> score.getAp();
            case SCORE -> score.getScore() != null ? BigDecimal.valueOf(score.getScore()) : null;
            case STREAK_115 -> score.getStreak115() != null ? BigDecimal.valueOf(score.getStreak115()) : null;
            case FC -> isFullCombo(score) ? BigDecimal.ONE : BigDecimal.ZERO;
            case RANK -> score.getRank() != null ? BigDecimal.valueOf(score.getRank()) : null;
            case PASS -> nfScoreIds.contains(score.getId()) ? BigDecimal.ZERO : BigDecimal.ONE;
        };
    }

    public static BigDecimal bestAccuracy(UserMapDifficultyBests bests) {
        if (bests.maxScore() == null || bests.maxScore() == 0 || bests.bestScoreNoMods() == null) {
            return null;
        }
        return BigDecimal.valueOf(bests.bestScoreNoMods())
                .divide(BigDecimal.valueOf(bests.maxScore()), 6, RoundingMode.HALF_UP);
    }

    public static BigDecimal requirementValue(UserMapDifficultyBests bests, CampaignRequirementType type) {
        return switch (type) {
            case ACC -> bestAccuracy(bests);
            case AP -> bests.bestAp();
            case SCORE -> toDecimal(bests.bestScore());
            case STREAK_115 -> toDecimal(bests.bestStreak115());
            case FC -> bests.hasFullCombo() ? BigDecimal.ONE : BigDecimal.ZERO;
            case RANK -> toDecimal(bests.bestRank());
            case PASS -> bests.hasNoNfPass() ? BigDecimal.ONE : BigDecimal.ZERO;
        };
    }

    public static BigDecimal barrierMetric(UserMapDifficultyBests bests, BarrierConditionType type) {
        return switch (type) {
            case AVERAGE_ACC, ACC_MAX -> bestAccuracy(bests);
            case AVERAGE_AP, AP_MAX -> bests.bestAp();
            case STREAK_115_AVERAGE, STREAK_115_MAX -> toDecimal(bests.bestStreak115());
            case AVERAGE_RANK, MAX_RANK -> toDecimal(bests.bestRank());
            case FC, COMPLETION_COUNT, PASS -> null;
        };
    }

    private static BigDecimal toDecimal(Integer value) {
        return value != null ? BigDecimal.valueOf(value) : null;
    }

    public static BigDecimal toDisplayPrecision(BigDecimal value, CampaignRequirementType type) {
        return type == CampaignRequirementType.ACC ? displayAcc(value) : value;
    }

    public static BigDecimal toDisplayPrecision(BigDecimal value, BarrierConditionType type) {
        return type == BarrierConditionType.AVERAGE_ACC || type == BarrierConditionType.ACC_MAX
                ? displayAcc(value)
                : value;
    }

    private static BigDecimal displayAcc(BigDecimal acc) {
        return acc == null ? null : acc.setScale(4, RoundingMode.HALF_UP);
    }

    public static boolean isMaxAggregate(BarrierConditionType type) {
        return type == BarrierConditionType.AP_MAX
                || type == BarrierConditionType.ACC_MAX
                || type == BarrierConditionType.STREAK_115_MAX
                || type == BarrierConditionType.MAX_RANK;
    }

    public static BigDecimal max(List<BigDecimal> values) {
        BigDecimal maximum = values.get(0);
        for (BigDecimal v : values) {
            if (v.compareTo(maximum) > 0) {
                maximum = v;
            }
        }
        return maximum;
    }

    public static BigDecimal average(List<BigDecimal> values) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            sum = sum.add(v);
        }
        return sum.divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP);
    }

    public static Instant effectiveTime(Score score) {
        return score.getTimeSet() != null ? score.getTimeSet() : score.getCreatedAt();
    }

    public static UserMapDifficultyBests reduceBests(UUID mapDifficultyId, Integer maxScore,
            Collection<Score> rows, Set<UUID> nfScoreIds) {
        if (rows.isEmpty()) {
            return null;
        }
        Integer bestScore = null;
        Integer bestScoreNoMods = null;
        BigDecimal bestAp = null;
        Integer bestStreak115 = null;
        Integer bestRank = null;
        int fcFlag = 0;
        int noNfFlag = 0;
        for (Score s : rows) {
            bestScore = maxOf(bestScore, s.getScore());
            bestScoreNoMods = maxOf(bestScoreNoMods, s.getScoreNoMods());
            if (s.getAp() != null && (bestAp == null || s.getAp().compareTo(bestAp) > 0)) {
                bestAp = s.getAp();
            }
            bestStreak115 = maxOf(bestStreak115, s.getStreak115());
            if (s.isActive() && s.getRank() != null && s.getRankWhenSet() != null) {
                int rank = Math.min(s.getRank(), s.getRankWhenSet());
                if (bestRank == null || rank < bestRank) {
                    bestRank = rank;
                }
            }
            if (isFullCombo(s)) {
                fcFlag = 1;
            }
            if (!nfScoreIds.contains(s.getId())) {
                noNfFlag = 1;
            }
        }
        return new UserMapDifficultyBests(mapDifficultyId, maxScore, bestScore, bestScoreNoMods,
                bestAp, bestStreak115, bestRank, fcFlag, noNfFlag);
    }

    private static Integer maxOf(Integer current, Integer candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate > current ? candidate : current;
    }
}
