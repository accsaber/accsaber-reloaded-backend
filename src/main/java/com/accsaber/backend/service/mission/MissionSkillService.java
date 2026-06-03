package com.accsaber.backend.service.mission;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
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
    private static final BigDecimal SKILL_GAP_FLOOR = new BigDecimal("10");
    private static final BigDecimal SKILL_GAP_HIGH = new BigDecimal("25");
    private static final BigDecimal SKILL_GAP_MAX = new BigDecimal("40");
    private static final BigDecimal LIFT_SHALLOW = new BigDecimal("0.40");
    private static final BigDecimal LIFT_MID = new BigDecimal("0.65");
    private static final BigDecimal LIFT_DEEP = new BigDecimal("0.85");
    private static final BigDecimal SKILL_LIFT_SHALLOW = new BigDecimal("0.18");
    private static final BigDecimal SKILL_LIFT_MID = new BigDecimal("0.30");
    private static final BigDecimal SKILL_LIFT_DEEP = new BigDecimal("0.45");
    private static final double PLAY_DAMPEN_FLOOR = 0.30;
    private static final double PLAY_DAMPEN_RATE = 0.70;

    private final ScoreRepository scoreRepository;

    public BigDecimal skillLevelFor(MissionAssignmentContext ctx, Category category) {
        if (category != null) {
            UserCategorySkill s = ctx.skillByCategoryId().get(category.getId());
            if (s != null && s.getSkillLevel() != null)
                return s.getSkillLevel();
        }
        return ctx.skillByCategoryId().values().stream()
                .filter(s -> s.getCategory() != null && OVERALL_CODE.equals(s.getCategory().getCode()))
                .filter(s -> s.getSkillLevel() != null)
                .findFirst()
                .map(UserCategorySkill::getSkillLevel)
                .orElse(BigDecimal.ZERO);
    }

    public BigDecimal liftedThreshold(MissionAssignmentContext ctx, Category targetCategory,
            BigDecimal categoryThreshold) {
        if (categoryThreshold == null || targetCategory == null)
            return categoryThreshold;
        UserCategorySkill targetSkill = ctx.skillByCategoryId().get(targetCategory.getId());
        BigDecimal targetSkillLevel = targetSkill != null && targetSkill.getSkillLevel() != null
                ? targetSkill.getSkillLevel()
                : BigDecimal.ZERO;
        UserCategorySkill bestOther = bestOtherSkill(ctx, targetCategory, true);
        if (bestOther == null)
            return categoryThreshold;
        BigDecimal skillGap = bestOther.getSkillLevel().subtract(targetSkillLevel);
        if (skillGap.compareTo(SKILL_GAP_FLOOR) < 0)
            return categoryThreshold;
        BigDecimal bestThreshold = bestOther.getRawApForOneGain();
        if (bestThreshold.compareTo(categoryThreshold) <= 0)
            return categoryThreshold;
        BigDecimal liftFraction = pickLiftFraction(skillGap, LIFT_DEEP, LIFT_MID, LIFT_SHALLOW);
        liftFraction = applyPlayDampener(ctx, targetCategory, bestOther.getCategory(), liftFraction);
        BigDecimal gap = bestThreshold.subtract(categoryThreshold);
        return categoryThreshold.add(gap.multiply(liftFraction));
    }

    public BigDecimal liftedSkillLevel(MissionAssignmentContext ctx, Category targetCategory,
            BigDecimal categorySkillLevel) {
        if (categorySkillLevel == null || targetCategory == null)
            return categorySkillLevel;
        UserCategorySkill bestOther = bestOtherSkill(ctx, targetCategory, false);
        if (bestOther == null)
            return categorySkillLevel;
        BigDecimal skillGap = bestOther.getSkillLevel().subtract(categorySkillLevel);
        if (skillGap.compareTo(SKILL_GAP_FLOOR) < 0)
            return categorySkillLevel;
        BigDecimal liftFraction = pickLiftFraction(skillGap, SKILL_LIFT_DEEP, SKILL_LIFT_MID, SKILL_LIFT_SHALLOW);
        liftFraction = applyPlayDampener(ctx, targetCategory, bestOther.getCategory(), liftFraction);
        return categorySkillLevel.add(skillGap.multiply(liftFraction));
    }

    public int representativeUserStreak(Long userId, UUID categoryId, MissionBand band) {
        List<Integer> top = scoreRepository.findTopStreak115ValuesByUserAndCategory(
                userId, categoryId, PageRequest.of(0, 10));
        if (top.isEmpty())
            return 0;
        int max = top.get(0);
        int median = top.get(Math.min(top.size() / 2, top.size() - 1));
        if (top.size() >= 5 && max > median * 1.5) {
            double multiplier = switch (band) {
                case easy -> 0.80;
                case medium -> 0.90;
                case hard -> 1.00;
                case extreme -> 1.10;
            };
            return Math.max(2, (int) Math.round(median * multiplier));
        }
        int effectiveTop = top.size() >= 2 && max > top.get(1) * 1.5 ? top.get(1) : max;
        return switch (band) {
            case easy -> top.get(Math.min(8, top.size() - 1));
            case medium -> top.get(Math.min(6, top.size() - 1));
            case hard -> top.get(Math.min(4, top.size() - 1));
            case extreme -> top.size() >= 3 ? Math.min(top.get(2), effectiveTop) : effectiveTop;
        };
    }

    public BigDecimal ageAdjustedUserAp(Score myScore, BigDecimal topAp) {
        BigDecimal scoreAp = myScore.getAp() != null ? myScore.getAp() : BigDecimal.ZERO;
        Instant when = myScore.getTimeSet() != null ? myScore.getTimeSet() : myScore.getCreatedAt();
        if (when == null || topAp == null || topAp.compareTo(scoreAp) <= 0)
            return scoreAp;
        long days = Duration.between(when, Instant.now()).toDays();
        if (days <= 0)
            return scoreAp;
        double agingFactor = Math.max(0.0, Math.min(1.0, (365.0 - days) / 365.0));
        double liftWeight = (1.0 - agingFactor) * 0.20;
        if (liftWeight <= 0)
            return scoreAp;
        BigDecimal lift = topAp.subtract(scoreAp).multiply(BigDecimal.valueOf(liftWeight));
        return scoreAp.add(lift);
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
                .filter(s -> s.getSkillLevel() != null)
                .filter(s -> !requireThreshold || s.getRawApForOneGain() != null)
                .max(Comparator.comparing(UserCategorySkill::getSkillLevel))
                .orElse(null);
    }

    private BigDecimal pickLiftFraction(BigDecimal skillGap, BigDecimal deep, BigDecimal mid, BigDecimal shallow) {
        if (skillGap.compareTo(SKILL_GAP_MAX) >= 0)
            return deep;
        if (skillGap.compareTo(SKILL_GAP_HIGH) >= 0)
            return mid;
        return shallow;
    }

    private BigDecimal applyPlayDampener(MissionAssignmentContext ctx, Category target, Category best,
            BigDecimal liftFraction) {
        Long targetPlays = ctx.rankedPlaysByCategoryId().get(target.getId());
        Long bestPlays = ctx.rankedPlaysByCategoryId().get(best.getId());
        if (targetPlays == null || bestPlays == null || bestPlays <= 0)
            return liftFraction;
        double playRatio = targetPlays.doubleValue() / bestPlays.doubleValue();
        if (playRatio >= 1.0)
            return BigDecimal.ZERO;
        double dampen = Math.max(PLAY_DAMPEN_FLOOR, 1.0 - playRatio * PLAY_DAMPEN_RATE);
        return liftFraction.multiply(BigDecimal.valueOf(dampen));
    }
}
