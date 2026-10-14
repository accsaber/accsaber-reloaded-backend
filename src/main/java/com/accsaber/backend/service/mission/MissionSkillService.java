package com.accsaber.backend.service.mission;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.mission.MissionBand;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.UserCategorySkill;
import com.accsaber.backend.repository.score.ScoreRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MissionSkillService {

    private static final String OVERALL_CODE = "overall";
    private static final double SKILL_GAP_FLOOR = 10;
    private static final double SKILL_GAP_HIGH = 25;
    private static final double SKILL_GAP_MAX = 40;
    private static final double LIFT_SHALLOW = 0.40;
    private static final double LIFT_MID = 0.65;
    private static final double LIFT_DEEP = 0.85;
    private static final double SKILL_LIFT_SHALLOW = 0.18;
    private static final double SKILL_LIFT_MID = 0.30;
    private static final double SKILL_LIFT_DEEP = 0.45;
    private static final double PLAY_DAMPEN_FLOOR = 0.30;
    private static final double PLAY_DAMPEN_RATE = 0.70;
    private static final int STREAK_SAMPLE_SIZE = 30;
    private static final int STREAK_OUTLIER_WINDOW = 10;
    private static final double REFERENCE_DEPTH = 0.30;
    private static final int MIN_DEPTH_INDEX = 4;
    private static final int AP_SAMPLE_SIZE = 20;
    private static final int AP_MIN_SAMPLE = 5;
    private static final double ANY_COMPLEXITY_MIN = 0.0;
    private static final double ANY_COMPLEXITY_MAX = Double.MAX_VALUE;

    private final ScoreRepository scoreRepository;

    public Double skillLevelFor(MissionAssignmentContext ctx, Category category) {
        if (category != null) {
            UserCategorySkill s = ctx.skillByCategoryId().get(category.getId());
            if (s != null)
                return s.getSkillLevel();
        }
        return ctx.skillByCategoryId().values().stream()
                .filter(s -> s.getCategory() != null && OVERALL_CODE.equals(s.getCategory().getCode()))
                .findFirst()
                .map(UserCategorySkill::getSkillLevel)
                .orElse(0.0);
    }

    public Double skillThresholdFor(MissionAssignmentContext ctx, Category category) {
        if (category != null && !OVERALL_CODE.equals(category.getCode())) {
            UserCategorySkill s = ctx.skillByCategoryId().get(category.getId());
            if (s != null && s.getRawApForOneGain() != null) {
                return s.getRawApForOneGain();
            }
        }
        OptionalDouble mean = ctx.skillByCategoryId().values().stream()
                .filter(s -> s.getCategory() != null)
                .filter(s -> !OVERALL_CODE.equals(s.getCategory().getCode()))
                .map(UserCategorySkill::getRawApForOneGain)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average();
        return mean.isPresent() ? mean.getAsDouble() : null;
    }

    public Double liftedThreshold(MissionAssignmentContext ctx, Category targetCategory,
            Double categoryThreshold) {
        if (categoryThreshold == null || targetCategory == null)
            return categoryThreshold;
        UserCategorySkill targetSkill = ctx.skillByCategoryId().get(targetCategory.getId());
        double targetSkillLevel = targetSkill != null
                ? targetSkill.getSkillLevel()
                : 0.0;
        UserCategorySkill bestOther = bestOtherSkill(ctx, targetCategory, true);
        if (bestOther == null)
            return categoryThreshold;
        double skillGap = bestOther.getSkillLevel() - targetSkillLevel;
        if (skillGap < SKILL_GAP_FLOOR)
            return categoryThreshold;
        double bestThreshold = bestOther.getRawApForOneGain();
        if (bestThreshold <= categoryThreshold)
            return categoryThreshold;
        double liftFraction = pickLiftFraction(skillGap, LIFT_DEEP, LIFT_MID, LIFT_SHALLOW);
        liftFraction = applyPlayDampener(ctx, targetCategory, bestOther.getCategory(), liftFraction);
        double gap = bestThreshold - categoryThreshold;
        return categoryThreshold + gap * liftFraction;
    }

    public Double liftedSkillLevel(MissionAssignmentContext ctx, Category targetCategory,
            Double categorySkillLevel) {
        if (categorySkillLevel == null || targetCategory == null)
            return categorySkillLevel;
        UserCategorySkill bestOther = bestOtherSkill(ctx, targetCategory, false);
        if (bestOther == null)
            return categorySkillLevel;
        double skillGap = bestOther.getSkillLevel() - categorySkillLevel;
        if (skillGap < SKILL_GAP_FLOOR)
            return categorySkillLevel;
        double liftFraction = pickLiftFraction(skillGap, SKILL_LIFT_DEEP, SKILL_LIFT_MID, SKILL_LIFT_SHALLOW);
        liftFraction = applyPlayDampener(ctx, targetCategory, bestOther.getCategory(), liftFraction);
        return categorySkillLevel + skillGap * liftFraction;
    }

    public record RepresentativeStreak(int value, boolean fromComplexityBand) {
    }

    public int representativeUserStreak(Long userId, UUID categoryId, MissionBand band) {
        List<Integer> top = scoreRepository.findTopStreak115ValuesByUserAndCategory(
                userId, categoryId, null, null, PageRequest.of(0, STREAK_SAMPLE_SIZE));
        return representativeStreakFromTop(top, band);
    }

    public RepresentativeStreak representativeUserStreakForComplexityBand(Long userId, UUID categoryId,
            MissionBand band, double complexityMin, double complexityMaxExclusive) {
        List<Integer> top = scoreRepository.findTopStreak115ValuesByUserAndCategory(
                userId, categoryId, complexityMin, complexityMaxExclusive,
                PageRequest.of(0, STREAK_SAMPLE_SIZE));
        if (top.isEmpty())
            return new RepresentativeStreak(representativeUserStreak(userId, categoryId, band), false);
        return new RepresentativeStreak(representativeStreakFromTop(top, band), true);
    }

    public Double representativeNormalizedApForComplexityBand(Long userId, UUID categoryId, double shift,
            double complexityMin, double complexityMaxExclusive) {
        Double banded = representativeNormalizedAp(userId, categoryId, shift, complexityMin, complexityMaxExclusive);
        if (banded != null)
            return banded;
        return representativeNormalizedAp(userId, categoryId, shift, ANY_COMPLEXITY_MIN, ANY_COMPLEXITY_MAX);
    }

    private Double representativeNormalizedAp(Long userId, UUID categoryId, double shift,
            double complexityMin, double complexityMaxExclusive) {
        List<Double> top = scoreRepository.findTopApPerComplexityByUserAndCategoryAndComplexityRange(
                userId, categoryId, complexityMin, complexityMaxExclusive, shift,
                PageRequest.of(0, AP_SAMPLE_SIZE));
        if (top.size() < AP_MIN_SAMPLE)
            return null;
        return top.get(referenceIndex(top.size()));
    }

    private int referenceIndex(int size) {
        return Math.min(size - 1, Math.max((int) Math.round((size - 1) * REFERENCE_DEPTH),
                Math.min(MIN_DEPTH_INDEX, size - 1)));
    }

    private int representativeStreakFromTop(List<Integer> top, MissionBand band) {
        if (top.isEmpty())
            return 0;
        List<Integer> head = top.subList(0, Math.min(STREAK_OUTLIER_WINDOW, top.size()));
        int max = head.get(0);
        int median = head.get(Math.min(head.size() / 2, head.size() - 1));
        if (head.size() >= 5 && max > median * 1.5) {
            double multiplier = switch (band) {
                case easy -> 0.80;
                case medium -> 0.90;
                case hard -> 1.05;
                case extreme -> 1.30;
            };
            return Math.max(2, (int) Math.round(median * multiplier));
        }
        return top.get(referenceIndex(top.size()));
    }

    public Double ageAdjustedUserAp(Score myScore, Double topAp) {
        double scoreAp = myScore.getAp();
        Instant when = myScore.getTimeSet() != null ? myScore.getTimeSet() : myScore.getCreatedAt();
        if (when == null || topAp == null || topAp <= scoreAp)
            return scoreAp;
        long days = Duration.between(when, Instant.now()).toDays();
        if (days <= 0)
            return scoreAp;
        double agingFactor = Math.max(0.0, Math.min(1.0, (365.0 - days) / 365.0));
        double liftWeight = (1.0 - agingFactor) * 0.20;
        if (liftWeight <= 0)
            return scoreAp;
        double lift = (topAp - scoreAp) * liftWeight;
        return scoreAp + lift;
    }

    public double pbFreshnessBoost(Score existing) {
        if (existing == null)
            return 1.0;
        Instant when = existing.getTimeSet() != null ? existing.getTimeSet() : existing.getCreatedAt();
        if (when == null)
            return 1.0;
        long days = Duration.between(when, Instant.now()).toDays();
        if (days <= 0)
            return 1.30;
        double freshness = Math.max(0.0, Math.min(1.0, (180.0 - days) / 180.0));
        return 1.0 + freshness * 0.30;
    }

    private UserCategorySkill bestOtherSkill(MissionAssignmentContext ctx, Category targetCategory,
            boolean requireThreshold) {
        return ctx.skillByCategoryId().values().stream()
                .filter(s -> s.getCategory() != null)
                .filter(s -> !OVERALL_CODE.equals(s.getCategory().getCode()))
                .filter(s -> !s.getCategory().getId().equals(targetCategory.getId()))
                .filter(s -> !requireThreshold || s.getRawApForOneGain() != null)
                .max(Comparator.comparingDouble(UserCategorySkill::getSkillLevel))
                .orElse(null);
    }

    private double pickLiftFraction(double skillGap, double deep, double mid, double shallow) {
        if (skillGap >= SKILL_GAP_MAX)
            return deep;
        if (skillGap >= SKILL_GAP_HIGH)
            return mid;
        return shallow;
    }

    private double applyPlayDampener(MissionAssignmentContext ctx, Category target, Category best,
            double liftFraction) {
        Long targetPlays = ctx.rankedPlaysByCategoryId().get(target.getId());
        Long bestPlays = ctx.rankedPlaysByCategoryId().get(best.getId());
        if (targetPlays == null || bestPlays == null || bestPlays <= 0)
            return liftFraction;
        double playRatio = (double) targetPlays / bestPlays;
        if (playRatio >= 1.0)
            return 0.0;
        double dampen = Math.max(PLAY_DAMPEN_FLOOR, 1.0 - playRatio * PLAY_DAMPEN_RATE);
        return liftFraction * dampen;
    }
}
