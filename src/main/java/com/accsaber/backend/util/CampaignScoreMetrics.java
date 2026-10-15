package com.accsaber.backend.util;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import com.accsaber.backend.model.dto.projection.UserMapDifficultyBests;
import com.accsaber.backend.model.entity.campaign.BarrierConditionType;
import com.accsaber.backend.model.entity.campaign.CampaignDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyTarget;
import com.accsaber.backend.model.entity.campaign.CampaignPrerequisiteMode;
import com.accsaber.backend.model.entity.campaign.CampaignRequirementType;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.score.Score;

public final class CampaignScoreMetrics {

    private CampaignScoreMetrics() {
    }

    public static Double accuracy(Score score) {
        MapDifficulty md = score.getMapDifficulty();
        return md == null ? null : accuracy(score.getScoreNoMods(), md.getMaxScore());
    }

    private static Double accuracy(Integer scoreNoMods, Integer maxScore) {
        if (maxScore == null || maxScore == 0 || scoreNoMods == null) {
            return null;
        }
        return Rounding.round((double) (scoreNoMods) / (double) (maxScore), 6);
    }

    private static boolean isFullCombo(Score score) {
        return score.getMisses() != null && score.getBadCuts() != null
                && score.getMisses() == 0 && score.getBadCuts() == 0;
    }

    private static Integer mistakes(Score score) {
        if (score.getBadCuts() == null || score.getMisses() == null) {
            return null;
        }
        return score.getBadCuts() + score.getMisses();
    }

    public static Double requirementValue(Score score, CampaignRequirementType type, ScoreModifierIndex modifiers) {
        return switch (type) {
            case ACC -> accuracy(score);
            case AP -> score.getAp();
            case SCORE -> score.getScore() != null ? (double) (score.getScore()) : null;
            case STREAK_115 -> score.getStreak115() != null ? (double) (score.getStreak115()) : null;
            case FC -> isFullCombo(score) ? 1.0 : 0.0;
            case RANK -> score.getRank() != null ? (double) (score.getRank()) : null;
            case PASS -> modifiers.hasNoFail(score.getId()) ? 0.0 : 1.0;
            case COMBO -> toDecimal(score.getMaxCombo());
            case BOMB_HITS -> toDecimal(score.getBombHits());
            case MISTAKES -> toDecimal(mistakes(score));
            case PAUSES -> toDecimal(score.getPauses());
        };
    }

    public static boolean satisfies(CampaignDifficultyTarget target, Double value) {
        CampaignRequirementType type = target.getRequirementType();
        return satisfiesBounds(
                toDisplayPrecision(value, type),
                toDisplayPrecision(target.getRequirementValue(), type),
                toDisplayPrecision(target.getRequirementValueMax(), type),
                type.isLowerBetter());
    }

    public static boolean satisfiesBounds(Double value, Double bound, Double cap,
            boolean lowerBetter) {
        if (value == null || (bound == null && cap == null)) {
            return false;
        }
        if (bound != null) {
            int cmp = value.compareTo(bound);
            if (lowerBetter ? cmp > 0 : cmp < 0) {
                return false;
            }
        }
        return cap == null || value.compareTo(cap) <= 0;
    }

    public static Double rowValue(Score row, CampaignRequirementType type, ScoreModifierIndex modifiers) {
        if (type != CampaignRequirementType.RANK) {
            return requirementValue(row, type, modifiers);
        }
        if (!row.isActive() || row.getRank() == null || row.getRankWhenSet() == null) {
            return null;
        }
        return (double) (Math.min(row.getRank(), row.getRankWhenSet()));
    }

    public static List<CampaignDifficultyTarget> effectiveTargets(CampaignDifficulty difficulty,
            List<CampaignDifficultyTarget> stored) {
        if (!stored.isEmpty()) {
            return stored;
        }
        if (difficulty.getRequirementType() == null) {
            return List.of();
        }
        return List.of(CampaignDifficultyTarget.builder()
                .requirementType(difficulty.getRequirementType())
                .requirementValue(difficulty.getRequirementValue())
                .requirementValueMax(difficulty.getRequirementValueMax())
                .build());
    }

    public static boolean requiresAllTargets(CampaignDifficulty difficulty) {
        return difficulty.getTargetMode() != CampaignPrerequisiteMode.OR;
    }

    public static boolean targetsMet(List<CampaignDifficultyTarget> targets, boolean requireAll,
            Function<CampaignRequirementType, Double> valueOf) {
        if (targets.isEmpty()) {
            return false;
        }
        for (CampaignDifficultyTarget target : targets) {
            if (satisfies(target, valueOf.apply(target.getRequirementType())) != requireAll) {
                return !requireAll;
            }
        }
        return requireAll;
    }

    public static Score bestMatchingRow(Collection<Score> rows, List<CampaignDifficultyTarget> targets,
            boolean requireAll, ScoreModifierIndex modifiers) {
        return rows.stream()
                .map(row -> new Match(row, targetShortfall(row, targets, requireAll, modifiers)))
                .min(MATCH_ORDER)
                .map(Match::row)
                .orElse(null);
    }

    private record Match(Score row, double shortfall) {
    }

    private static final Comparator<Match> MATCH_ORDER = Comparator
            .comparingDouble(Match::shortfall)
            .thenComparing(m -> m.row().getScoreNoMods(), Comparator.nullsLast(Comparator.reverseOrder()));

    private static double targetShortfall(Score row, List<CampaignDifficultyTarget> targets, boolean requireAll,
            ScoreModifierIndex modifiers) {
        if (targets.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        double total = requireAll ? 0.0 : Double.POSITIVE_INFINITY;
        for (CampaignDifficultyTarget target : targets) {
            double gap = gap(target, rowValue(row, target.getRequirementType(), modifiers));
            total = requireAll ? total + gap : Math.min(total, gap);
        }
        return total;
    }

    private static double gap(CampaignDifficultyTarget target, Double value) {
        CampaignRequirementType type = target.getRequirementType();
        Double actual = toDisplayPrecision(value, type);
        Double bound = toDisplayPrecision(target.getRequirementValue(), type);
        Double cap = toDisplayPrecision(target.getRequirementValueMax(), type);
        if (actual == null || (bound == null && cap == null)) {
            return Double.POSITIVE_INFINITY;
        }
        if (satisfiesBounds(actual, bound, cap, type.isLowerBetter())) {
            return 0.0;
        }
        double scale = Math.max(Math.abs(bound != null ? bound : cap), 1e-9);
        if (bound != null) {
            double missedBound = type.isLowerBetter() ? actual - bound : bound - actual;
            if (missedBound > 0) {
                return missedBound / scale;
            }
        }
        return (actual - cap) / scale;
    }

    private static Double bestAccuracy(UserMapDifficultyBests bests) {
        return accuracy(bests.bestScoreNoMods(), bests.maxScore());
    }

    public static Double barrierMetric(UserMapDifficultyBests bests, BarrierConditionType type) {
        return switch (type) {
            case AVERAGE_ACC, ACC_MAX -> bestAccuracy(bests);
            case AVERAGE_AP, AP_MAX -> bests.bestAp();
            case STREAK_115_AVERAGE, STREAK_115_MAX -> toDecimal(bests.bestStreak115());
            case AVERAGE_RANK, MAX_RANK -> toDecimal(bests.bestRank());
            case AVERAGE_COMBO -> toDecimal(bests.bestCombo());
            case AVERAGE_BOMB_HITS -> toDecimal(bests.fewestBombHits());
            case AVERAGE_MISTAKES -> toDecimal(bests.fewestMistakes());
            case AVERAGE_PAUSES -> toDecimal(bests.fewestPauses());
            case FC, COMPLETION_COUNT, PASS -> null;
        };
    }

    private static Double toDecimal(Integer value) {
        return value != null ? (double) (value) : null;
    }

    public static Double toDisplayPrecision(Double value, CampaignRequirementType type) {
        return type == CampaignRequirementType.ACC ? displayAcc(value) : value;
    }

    public static Double toDisplayPrecision(Double value, BarrierConditionType type) {
        return type == BarrierConditionType.AVERAGE_ACC || type == BarrierConditionType.ACC_MAX
                ? displayAcc(value)
                : value;
    }

    private static Double displayAcc(Double acc) {
        return acc == null ? null : Rounding.round(acc, 4);
    }

    public static boolean isMaxAggregate(BarrierConditionType type) {
        return type == BarrierConditionType.AP_MAX
                || type == BarrierConditionType.ACC_MAX
                || type == BarrierConditionType.STREAK_115_MAX
                || type == BarrierConditionType.MAX_RANK;
    }

    public static Double max(List<Double> values) {
        return Collections.max(values);
    }

    public static Double average(List<Double> values) {
        return Rounding.round(values.stream().reduce(0.0, Double::sum) / values.size(), 6);
    }

    public static Instant effectiveTime(Score score) {
        return score.getTimeSet() != null ? score.getTimeSet() : score.getCreatedAt();
    }

    public static UserMapDifficultyBests reduceBests(UUID mapDifficultyId, Integer maxScore,
            Collection<Score> rows, ScoreModifierIndex modifiers) {
        if (rows.isEmpty()) {
            return null;
        }
        Integer bestScore = null;
        Integer bestScoreNoMods = null;
        Double bestAp = null;
        Integer bestStreak115 = null;
        Integer bestRank = null;
        Integer bestCombo = null;
        Integer fewestBombHits = null;
        Integer fewestMistakes = null;
        Integer fewestPauses = null;
        int fcFlag = 0;
        int noNfFlag = 0;
        for (Score s : rows) {
            bestCombo = maxOf(bestCombo, s.getMaxCombo());
            fewestBombHits = minOf(fewestBombHits, s.getBombHits());
            fewestMistakes = minOf(fewestMistakes, mistakes(s));
            fewestPauses = minOf(fewestPauses, s.getPauses());
            bestScore = maxOf(bestScore, s.getScore());
            bestScoreNoMods = maxOf(bestScoreNoMods, s.getScoreNoMods());
            if (bestAp == null || s.getAp() > bestAp) {
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
            if (!modifiers.hasNoFail(s.getId())) {
                noNfFlag = 1;
            }
        }
        return new UserMapDifficultyBests(mapDifficultyId, maxScore, bestScore, bestScoreNoMods,
                bestAp, bestStreak115, bestRank, fcFlag, noNfFlag, bestCombo, fewestBombHits,
                fewestMistakes, fewestPauses);
    }

    private static Integer maxOf(Integer current, Integer candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate > current ? candidate : current;
    }

    private static Integer minOf(Integer current, Integer candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate < current ? candidate : current;
    }
}
