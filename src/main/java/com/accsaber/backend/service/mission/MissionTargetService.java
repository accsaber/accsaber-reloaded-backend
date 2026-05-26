package com.accsaber.backend.service.mission;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.mission.MissionBand;
import com.accsaber.backend.model.entity.user.UserCategorySkill;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MissionTargetService {

    private static final BigDecimal WR_DENSITY_THRESHOLD = new BigDecimal("0.85");
    private static final double WR_DENSITY_SLOPE = 0.40;
    private static final double CLIMB_GAP_THRESHOLD = 0.03;
    private static final double CLIMB_GAP_SLOPE = 0.70;
    private static final double DAMPEN_FLOOR = 0.90;
    private static final BigDecimal MAP_BLEND_WEIGHT = new BigDecimal("0.70");
    private static final BigDecimal SKILL_BLEND_WEIGHT = new BigDecimal("0.30");

    private final ScoreRepository scoreRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final MissionCalibrationService calibrationService;

    public MapPick sampleEligibleMap(Category category, BigDecimal threshold, BigDecimal multiplier,
            Curve scoreCurve, Random rng) {
        MissionCalibrationService.ComplexityRange range = calibrationService.complexityRange(threshold, multiplier,
                scoreCurve);
        if (range == null)
            return null;
        List<Object[]> rows = mapDifficultyRepository.findRankedWithComplexityInRange(
                category.getId(), range.min(), range.max());
        if (rows.isEmpty())
            return null;
        Object[] row = rows.get(rng.nextInt(rows.size()));
        MapDifficulty diff = (MapDifficulty) row[0];
        BigDecimal complexity = (BigDecimal) row[1];
        return new MapPick(diff, complexity, diff.getMaxScore());
    }

    public BigDecimal mapAwareTarget(UUID mapDifficultyId, UUID categoryId, double userSkill,
            BigDecimal userExistingAp, MissionBand band) {
        List<Object[]> rows = scoreRepository.findLeaderboardApAndSkill(mapDifficultyId, categoryId);
        if (rows.isEmpty())
            return null;
        int size = rows.size();
        int naturalIdx = naturalRankFor(rows, userSkill, userExistingAp);
        int rankShift = switch (band) {
            case easy -> Math.max(1, (int) Math.round(naturalIdx * 0.10));
            case medium -> 0;
            case hard -> -Math.max(2, (int) Math.round(naturalIdx * 0.30));
            case extreme -> -Math.max(3, (int) Math.round(naturalIdx * 0.50));
        };
        int targetIdx = Math.max(0, Math.min(size - 1, naturalIdx + rankShift));
        return (BigDecimal) rows.get(targetIdx)[0];
    }

    public BigDecimal blendSkillAndMapTarget(BigDecimal skillAnchored, BigDecimal mapTarget) {
        if (mapTarget == null)
            return skillAnchored;
        if (skillAnchored == null)
            return mapTarget;
        return mapTarget.multiply(MAP_BLEND_WEIGHT).add(skillAnchored.multiply(SKILL_BLEND_WEIGHT));
    }

    public BigDecimal capExtremeAtTopAp(BigDecimal targetRawAp, MissionBand band, UserCategorySkill skill) {
        if (skill.getTopAp() == null || skill.getTopAp().signum() <= 0)
            return targetRawAp;
        double factor = switch (band) {
            case easy -> 0.96;
            case medium -> 0.97;
            case hard -> 0.98;
            case extreme -> 1.005;
        };
        return targetRawAp.min(skill.getTopAp().multiply(BigDecimal.valueOf(factor)));
    }

    public BigDecimal capAtMapRealisticCeiling(BigDecimal targetRawAp, MapPick pick, Curve scoreCurve,
            MissionBand band, MissionPoolCache cache, BigDecimal skillLevel) {
        BigDecimal ceilingFraction = skillAwareBandFraction(band, skillLevel);
        BigDecimal wr = resolveMapWr(pick, cache);
        if (wr.signum() > 0)
            return targetRawAp.min(wr.multiply(ceilingFraction));
        BigDecimal fallback = calibrationService.maxRealisticRawAp(pick.complexity(), scoreCurve);
        if (fallback == null || fallback.signum() <= 0)
            return targetRawAp;
        return targetRawAp.min(fallback.multiply(ceilingFraction));
    }

    public BigDecimal applyLeaderboardDensityDampener(BigDecimal targetRawAp, MissionBand band,
            MapPick pick, MissionPoolCache cache, BigDecimal userCurrentAp) {
        if (targetRawAp == null || targetRawAp.signum() <= 0)
            return targetRawAp;
        if (band != MissionBand.hard && band != MissionBand.extreme)
            return targetRawAp;
        BigDecimal wr = cache.mapWrApByDifficulty().get(pick.difficulty().getId());
        if (wr == null || wr.signum() <= 0)
            return targetRawAp;
        double targetRatio = targetRawAp.doubleValue() / wr.doubleValue();
        if (targetRatio <= WR_DENSITY_THRESHOLD.doubleValue())
            return targetRawAp;
        double dampen;
        if (userCurrentAp != null && userCurrentAp.signum() > 0) {
            double userRatio = userCurrentAp.doubleValue() / wr.doubleValue();
            double climbGap = targetRatio - userRatio;
            if (climbGap <= CLIMB_GAP_THRESHOLD)
                return targetRawAp;
            dampen = 1.0 - (climbGap - CLIMB_GAP_THRESHOLD) * CLIMB_GAP_SLOPE;
        } else {
            dampen = 1.0 - (targetRatio - WR_DENSITY_THRESHOLD.doubleValue()) * WR_DENSITY_SLOPE;
        }
        dampen = Math.max(DAMPEN_FLOOR, dampen);
        return targetRawAp.multiply(BigDecimal.valueOf(dampen));
    }

    public BigDecimal mapWrFloorForBand(MissionBand band) {
        return switch (band) {
            case easy -> new BigDecimal("0.80");
            case medium -> new BigDecimal("0.86");
            case hard -> new BigDecimal("0.90");
            case extreme -> new BigDecimal("0.94");
        };
    }

    public MissionBand bandFromWeightedRatio(BigDecimal weighted, BigDecimal maxWeighted) {
        if (weighted == null || maxWeighted == null || maxWeighted.signum() <= 0)
            return MissionBand.medium;
        double ratio = weighted.doubleValue() / maxWeighted.doubleValue();
        if (ratio >= 0.80)
            return MissionBand.extreme;
        if (ratio >= 0.40)
            return MissionBand.hard;
        if (ratio >= 0.10)
            return MissionBand.medium;
        return MissionBand.easy;
    }

    public MissionBand blendBands(MissionBand assigned, MissionBand derived) {
        if (assigned == null)
            return derived;
        if (derived == null)
            return assigned;
        double blended = 0.6 * assigned.ordinal() + 0.4 * derived.ordinal();
        int idx = (int) Math.round(blended);
        MissionBand[] all = MissionBand.values();
        return all[Math.min(all.length - 1, Math.max(0, idx))];
    }

    public BigDecimal resolveMapWr(MapPick pick, MissionPoolCache cache) {
        return cache.mapWrApByDifficulty().computeIfAbsent(pick.difficulty().getId(), id -> {
            BigDecimal val = scoreRepository.findMaxApByMapDifficulty(id);
            return val != null ? val : BigDecimal.ZERO;
        });
    }

    private int naturalRankFor(List<Object[]> leaderboardDesc, double userSkill, BigDecimal userExistingAp) {
        int size = leaderboardDesc.size();
        if (userExistingAp != null && userExistingAp.signum() > 0) {
            for (int i = 0; i < size; i++) {
                BigDecimal candidateAp = (BigDecimal) leaderboardDesc.get(i)[0];
                if (candidateAp.compareTo(userExistingAp) <= 0)
                    return i;
            }
            return size;
        }
        for (int i = 0; i < size; i++) {
            BigDecimal candidateSkill = (BigDecimal) leaderboardDesc.get(i)[1];
            if (candidateSkill.doubleValue() <= userSkill)
                return i;
        }
        return size;
    }

    private BigDecimal skillAwareBandFraction(MissionBand band, BigDecimal skillLevel) {
        double skill = skillLevel != null ? Math.min(100.0, Math.max(0.0, skillLevel.doubleValue())) : 50.0;
        double skillAdj = Math.max(0.0, (skill - 50.0) / 50.0);
        double frac = switch (band) {
            case easy -> 0.75 + skillAdj * 0.10;
            case medium -> 0.82 + skillAdj * 0.10;
            case hard -> 0.88 + skillAdj * 0.08;
            case extreme -> 0.94 + skillAdj * 0.08;
        };
        return BigDecimal.valueOf(frac);
    }
}
