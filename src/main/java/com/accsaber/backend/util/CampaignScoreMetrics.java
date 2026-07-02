package com.accsaber.backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

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

    public static BigDecimal requirementValue(Score score, CampaignRequirementType type) {
        return switch (type) {
            case ACC -> accuracy(score);
            case AP -> score.getAp();
            case SCORE -> score.getScore() != null ? BigDecimal.valueOf(score.getScore()) : null;
            case STREAK_115 -> score.getStreak115() != null ? BigDecimal.valueOf(score.getStreak115()) : null;
            case FC -> isFullCombo(score) ? BigDecimal.ONE : BigDecimal.ZERO;
            case RANK -> score.getRank() != null ? BigDecimal.valueOf(score.getRank()) : null;
        };
    }

    public static BigDecimal barrierMetric(Score score, BarrierConditionType type) {
        return switch (type) {
            case AVERAGE_ACC, ACC_MAX -> accuracy(score);
            case AVERAGE_AP, AP_MAX -> score.getAp();
            case STREAK_115_AVERAGE, STREAK_115_MAX ->
                score.getStreak115() != null ? BigDecimal.valueOf(score.getStreak115()) : null;
            case AVERAGE_RANK, MAX_RANK ->
                score.getRank() != null ? BigDecimal.valueOf(score.getRank()) : null;
            case FC -> null;
        };
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
}
