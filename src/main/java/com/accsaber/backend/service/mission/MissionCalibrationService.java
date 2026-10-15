package com.accsaber.backend.service.mission;

import com.accsaber.backend.util.Rounding;

import org.springframework.stereotype.Service;

import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.mission.MissionBand;
import com.accsaber.backend.model.entity.mission.MissionTemplate;
import com.accsaber.backend.service.score.APCalculationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MissionCalibrationService {

    private static final double ACC_CEILING = 0.9995;
    private static final double NORM_AP_MIN = 0.30;
    private static final double NORM_AP_MAX = 0.95;
    private static final double CEILING_EPSILON = 0.005;
    private static final double REALISTIC_ACC_CAP = 0.995;
    private static final double SNIPE_BOOST_DIVISOR = 500.0;
    private static final double SNIPE_BOOST_CAP = 0.5;

    private final APCalculationService apCalculationService;

    private static final double EXTREME_BOOST = 1.35;

    public Double bandMultiplier(MissionTemplate template, MissionBand band) {
        return switch (band) {
            case easy -> template.getBandEasy();
            case medium -> template.getBandMedium();
            case hard -> template.getBandHard();
            case extreme -> (template.getBandHard() * EXTREME_BOOST);
        };
    }

    private Double targetNormalizedAp(Double targetRawAp, Double complexity, Curve scoreCurve) {
        Double scale = scoreCurve.getScale() != null ? scoreCurve.getScale() : 1.0;
        Double shift = scoreCurve.getShift() != null ? scoreCurve.getShift() : 0.0;
        Double denom =((complexity - shift) * scale);
        if (Math.signum(denom) <= 0) {
            return null;
        }
        return (targetRawAp / denom);
    }

    public Double targetAccuracy(Double targetRawAp, Double complexity, Curve scoreCurve,
            Double peakRawAp) {
        Double normalized = targetNormalizedAp(targetRawAp, complexity, scoreCurve);
        if (normalized == null) {
            return null;
        }
        Double acc = apCalculationService.inverseInterpolate(scoreCurve, normalized);
        Double clamp = peakAccuracyClamp(peakRawAp, complexity, scoreCurve);
        return Math.min(Math.min(acc, clamp), ACC_CEILING);
    }

    public Double maxRealisticRawAp(Double complexity, Curve scoreCurve) {
        Double scale = scoreCurve.getScale() != null ? scoreCurve.getScale() : 1.0;
        Double shift = scoreCurve.getShift() != null ? scoreCurve.getShift() : 0.0;
        double normalized = apCalculationService.interpolate(scoreCurve, REALISTIC_ACC_CAP);
        return normalized * (complexity - shift) * scale;
    }

    public Double bandLiftedFloorAp(Double existingAp, Double complexity, Curve scoreCurve,
            MissionBand band) {
        if (existingAp == null || Math.signum(existingAp) <= 0) {
            return null;
        }
        Double existingNormalized = targetNormalizedAp(existingAp, complexity, scoreCurve);
        if (existingNormalized == null) {
            return (existingAp + 1.0);
        }
        Double absoluteStep = switch (band) {
            case easy -> 0.015;
            case medium -> 0.030;
            case hard -> 0.055;
            case extreme -> 0.090;
        };
        Double headroomFraction = switch (band) {
            case easy -> 0.15;
            case medium -> 0.30;
            case hard -> 0.50;
            case extreme -> 0.75;
        };
        Double headroom =Math.max((1.0 - existingNormalized), 0.0);
        Double step = Math.min(absoluteStep, (headroom * headroomFraction));
        Double liftedNormalized = (existingNormalized + step);
        Double scale = scoreCurve.getScale() != null ? scoreCurve.getScale() : 1.0;
        Double shift = scoreCurve.getShift() != null ? scoreCurve.getShift() : 0.0;
        return liftedNormalized * (complexity - shift) * scale;
    }

    public ComplexityRange complexityRange(Double target, Curve scoreCurve) {
        if (target == null || Math.signum(target) <= 0) {
            return null;
        }
        Double scale = scoreCurve.getScale() != null ? scoreCurve.getScale() : 1.0;
        Double shift = scoreCurve.getShift() != null ? scoreCurve.getShift() : 0.0;
        Double minC =((target / (NORM_AP_MAX * scale)) + shift);
        Double maxC =((target / (NORM_AP_MIN * scale)) + shift);
        return new ComplexityRange(minC, maxC);
    }

    public int computeXpReward(MissionTemplate template, Double skillLevel,
            MissionBand band, Double snipeDistance) {
        if (template.getXpCurve() == null) {
            return 0;
        }
        Double lookupX = skillLevel != null ? skillLevel : 0.0;
        Double base = apCalculationService.interpolate(template.getXpCurve(), lookupX);
        Double bandMult = bandMultiplier(template, band);
        Double snipeBoost = snipeDistance == null
                ? 1.0
                : (1.0 +Math.min((snipeDistance / SNIPE_BOOST_DIVISOR), SNIPE_BOOST_CAP));
        Double result =(((base * template.getXpMultiplier()) * bandMult) * snipeBoost);
        return (int) (Rounding.round(result, 0));
    }

    private Double peakAccuracyClamp(Double peakRawAp, Double complexity, Curve scoreCurve) {
        if (peakRawAp == null || Math.signum(peakRawAp) <= 0) {
            return ACC_CEILING;
        }
        Double normalized = targetNormalizedAp(peakRawAp, complexity, scoreCurve);
        if (normalized == null) {
            return ACC_CEILING;
        }
        Double acc = apCalculationService.inverseInterpolate(scoreCurve, Math.min(normalized, 1.0));
        return Math.min((acc + CEILING_EPSILON), ACC_CEILING);
    }

    public record ComplexityRange(Double min, Double max) {
    }
}
