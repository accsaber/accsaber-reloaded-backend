package com.accsaber.backend.service.mission;

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

    private static final Double WR_DENSITY_THRESHOLD = 0.85;
    private static final double WR_DENSITY_SLOPE = 0.40;
    private static final double CLIMB_GAP_THRESHOLD = 0.03;
    private static final double CLIMB_GAP_SLOPE = 0.70;
    private static final double DAMPEN_FLOOR = 0.90;
    private static final Double MAP_BLEND_WEIGHT = 0.70;
    private static final Double SKILL_BLEND_WEIGHT = 0.30;

    private final ScoreRepository scoreRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final MissionCalibrationService calibrationService;

    public MapPick sampleEligibleMap(Category category, Double targetAp, Curve scoreCurve, Random rng) {
        MissionCalibrationService.ComplexityRange range = calibrationService.complexityRange(targetAp, scoreCurve);
        if (range == null)
            return null;
        List<Object[]> rows = mapDifficultyRepository.findRankedWithComplexityInRange(
                category.getId(), range.min(), range.max());
        if (rows.isEmpty())
            return null;
        Object[] row = rows.get(rng.nextInt(rows.size()));
        MapDifficulty diff = (MapDifficulty) row[0];
        Double complexity = (Double) row[1];
        return new MapPick(diff, complexity, diff.getMaxScore());
    }

    public Double mapAwareTarget(UUID mapDifficultyId, UUID categoryId, double userSkill,
            Double userExistingAp, MissionBand band) {
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
        return (Double) rows.get(targetIdx)[0];
    }

    public Double blendSkillAndMapTarget(Double skillAnchored, Double mapTarget) {
        if (mapTarget == null)
            return skillAnchored;
        if (skillAnchored == null)
            return mapTarget;
        return((mapTarget * MAP_BLEND_WEIGHT) + (skillAnchored * SKILL_BLEND_WEIGHT));
    }

    public Double snipeBandFraction(MissionBand band) {
        return switch (band) {
            case easy -> 0.93;
            case medium -> 0.95;
            case hard -> 0.97;
            case extreme -> 0.985;
        };
    }

    public Double skillFloorFraction(MissionBand band) {
        return switch (band) {
            case easy -> 0.935;
            case medium -> 0.95;
            case hard -> 0.965;
            case extreme -> 0.975;
        };
    }

    public Double skillAnchor(Double threshold, MissionBand band, UserCategorySkill skill,
            Double skillLevel) {
        Double ceiling = topApCeiling(band, skill, skillLevel);
        if (ceiling == null)
            return threshold;
        Double headroom =Math.max((ceiling - threshold), 0.0);
        return Math.min((threshold + (headroom * anchorFraction(band))), ceiling);
    }

    public Double capExtremeAtTopAp(Double targetRawAp, MissionBand band, UserCategorySkill skill,
            Double skillLevel) {
        Double ceiling = topApCeiling(band, skill, skillLevel);
        return ceiling == null ? targetRawAp : Math.min(targetRawAp, ceiling);
    }

    private Double topApCeiling(MissionBand band, UserCategorySkill skill, Double skillLevel) {
        if (skill == null || skill.getTopAp() <= 0)
            return null;
        double factor = switch (band) {
            case easy -> 0.96;
            case medium -> 0.97;
            case hard -> 0.98;
            case extreme -> 1.005;
        };
        Double baseCap = (skill.getTopAp() * (double) (factor));
        return band == MissionBand.extreme ? baseCap : applySkillAwareTopApNerf(baseCap, skillLevel);
    }

    private Double anchorFraction(MissionBand band) {
        return switch (band) {
            case easy -> 0.10;
            case medium -> 0.30;
            case hard -> 0.55;
            case extreme -> 0.85;
        };
    }

    public Double applySkillAwareTopApNerf(Double baseCap, Double skillLevel) {
        if (baseCap == null || Math.signum(baseCap) <= 0)
            return baseCap;
        double skill = skillLevel != null ? Math.max(0.0, skillLevel) : 50.0;
        if (skill >= 70.0)
            return baseCap;
        double t = (70.0 - skill) / 70.0;
        double smoothstep = t * t * (3.0 - 2.0 * t);
        double adjustment = smoothstep * 0.07;
        return (baseCap * (double) (1.0 - adjustment));
    }

    public Double capAtMapRealisticCeiling(Double targetRawAp, MapPick pick, Curve scoreCurve,
            MissionBand band, MissionPoolCache cache, Double skillLevel) {
        Double ceilingFraction = skillAwareBandFraction(band, skillLevel);
        Double wr = resolveMapWr(pick, cache);
        if (Math.signum(wr) > 0)
            return Math.min(targetRawAp, (wr * ceilingFraction));
        Double fallback = calibrationService.maxRealisticRawAp(pick.complexity(), scoreCurve);
        if (fallback == null || Math.signum(fallback) <= 0)
            return targetRawAp;
        return Math.min(targetRawAp, (fallback * ceilingFraction));
    }

    public Double applyLeaderboardDensityDampener(Double targetRawAp, MissionBand band,
            MapPick pick, MissionPoolCache cache, Double userCurrentAp) {
        if (targetRawAp == null || Math.signum(targetRawAp) <= 0)
            return targetRawAp;
        if (band != MissionBand.hard && band != MissionBand.extreme)
            return targetRawAp;
        Double wr = cache.mapWrApByDifficulty().get(pick.difficulty().getId());
        if (wr == null || Math.signum(wr) <= 0)
            return targetRawAp;
        double targetRatio = targetRawAp / wr;
        if (targetRatio <= WR_DENSITY_THRESHOLD)
            return targetRawAp;
        double dampen;
        if (userCurrentAp != null && Math.signum(userCurrentAp) > 0) {
            double userRatio = userCurrentAp / wr;
            double climbGap = targetRatio - userRatio;
            if (climbGap <= CLIMB_GAP_THRESHOLD)
                return targetRawAp;
            dampen = 1.0 - (climbGap - CLIMB_GAP_THRESHOLD) * CLIMB_GAP_SLOPE;
        } else {
            dampen = 1.0 - (targetRatio - WR_DENSITY_THRESHOLD) * WR_DENSITY_SLOPE;
        }
        dampen = Math.max(DAMPEN_FLOOR, dampen);
        return (targetRawAp * (double) (dampen));
    }

    public Double mapWrFloorForBand(MissionBand band) {
        return switch (band) {
            case easy -> 0.80;
            case medium -> 0.86;
            case hard -> 0.90;
            case extreme -> 0.94;
        };
    }

    public MissionBand bandFromWeightedRatio(Double weighted, Double maxWeighted) {
        if (weighted == null || maxWeighted == null || Math.signum(maxWeighted) <= 0)
            return MissionBand.medium;
        double ratio = weighted / maxWeighted;
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

    public Double resolveMapWr(MapPick pick, MissionPoolCache cache) {
        return cache.mapWrApByDifficulty().computeIfAbsent(pick.difficulty().getId(), id -> {
            Double val = scoreRepository.findMaxApByMapDifficulty(id);
            return val != null ? val : 0.0;
        });
    }

    private int naturalRankFor(List<Object[]> leaderboardDesc, double userSkill, Double userExistingAp) {
        int size = leaderboardDesc.size();
        if (userExistingAp != null && Math.signum(userExistingAp) > 0) {
            for (int i = 0; i < size; i++) {
                Double candidateAp = (Double) leaderboardDesc.get(i)[0];
                if (candidateAp.compareTo(userExistingAp) <= 0)
                    return i;
            }
            return size;
        }
        for (int i = 0; i < size; i++) {
            Double candidateSkill = (Double) leaderboardDesc.get(i)[1];
            if (candidateSkill <= userSkill)
                return i;
        }
        return size;
    }

    private Double skillAwareBandFraction(MissionBand band, Double skillLevel) {
        double skill = skillLevel != null ? Math.min(100.0, Math.max(0.0, skillLevel)) : 50.0;
        double skillAdj = Math.max(0.0, (skill - 50.0) / 50.0);
        double frac = switch (band) {
            case easy -> 0.75 + skillAdj * 0.10;
            case medium -> 0.82 + skillAdj * 0.10;
            case hard -> 0.88 + skillAdj * 0.08;
            case extreme -> 0.94 + skillAdj * 0.08;
        };
        return (double) (frac);
    }
}
