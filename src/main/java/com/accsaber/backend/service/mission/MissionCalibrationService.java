package com.accsaber.backend.service.mission;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.mission.MissionBand;
import com.accsaber.backend.model.entity.mission.MissionTemplate;
import com.accsaber.backend.service.score.APCalculationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MissionCalibrationService {

    private static final MathContext MATH = new MathContext(16, RoundingMode.HALF_UP);
    private static final BigDecimal ACC_CEILING = new BigDecimal("0.9995");
    private static final BigDecimal NORM_AP_MIN = new BigDecimal("0.30");
    private static final BigDecimal NORM_AP_MAX = new BigDecimal("0.95");
    private static final BigDecimal CEILING_EPSILON = new BigDecimal("0.005");
    private static final BigDecimal REALISTIC_ACC_CAP = new BigDecimal("0.995");
    private static final BigDecimal SNIPE_BOOST_DIVISOR = new BigDecimal("500");
    private static final BigDecimal SNIPE_BOOST_CAP = new BigDecimal("0.5");

    private final APCalculationService apCalculationService;

    private static final BigDecimal EXTREME_BOOST = new BigDecimal("1.35");

    public BigDecimal bandMultiplier(MissionTemplate template, MissionBand band) {
        return switch (band) {
            case easy -> template.getBandEasy();
            case medium -> template.getBandMedium();
            case hard -> template.getBandHard();
            case extreme -> template.getBandHard().multiply(EXTREME_BOOST, MATH);
        };
    }

    public BigDecimal targetRawAp(BigDecimal threshold, BigDecimal multiplier) {
        if (threshold == null) {
            return null;
        }
        return threshold.multiply(multiplier, MATH).setScale(6, RoundingMode.HALF_UP);
    }

    public BigDecimal targetNormalizedAp(BigDecimal targetRawAp, BigDecimal complexity, Curve scoreCurve) {
        BigDecimal scale = scoreCurve.getScale() != null ? scoreCurve.getScale() : BigDecimal.ONE;
        BigDecimal shift = scoreCurve.getShift() != null ? scoreCurve.getShift() : BigDecimal.ZERO;
        BigDecimal denom = complexity.subtract(shift).multiply(scale, MATH);
        if (denom.signum() <= 0) {
            return null;
        }
        return targetRawAp.divide(denom, MATH);
    }

    public BigDecimal targetAccuracy(BigDecimal targetRawAp, BigDecimal complexity, Curve scoreCurve,
            BigDecimal peakRawAp) {
        BigDecimal normalized = targetNormalizedAp(targetRawAp, complexity, scoreCurve);
        if (normalized == null) {
            return null;
        }
        BigDecimal acc = apCalculationService.inverseInterpolate(scoreCurve, normalized);
        BigDecimal clamp = peakAccuracyClamp(peakRawAp, complexity, scoreCurve);
        return acc.min(clamp).min(ACC_CEILING);
    }

    public boolean isComplexityViableForThreshold(BigDecimal threshold, BigDecimal multiplier,
            BigDecimal complexity, Curve scoreCurve) {
        BigDecimal target = targetRawAp(threshold, multiplier);
        BigDecimal normalized = targetNormalizedAp(target, complexity, scoreCurve);
        if (normalized == null) {
            return false;
        }
        return normalized.compareTo(NORM_AP_MIN) >= 0 && normalized.compareTo(NORM_AP_MAX) <= 0;
    }

    public BigDecimal maxRealisticRawAp(BigDecimal complexity, Curve scoreCurve) {
        BigDecimal scale = scoreCurve.getScale() != null ? scoreCurve.getScale() : BigDecimal.ONE;
        BigDecimal shift = scoreCurve.getShift() != null ? scoreCurve.getShift() : BigDecimal.ZERO;
        BigDecimal normalized = apCalculationService.interpolate(scoreCurve, REALISTIC_ACC_CAP);
        return normalized.multiply(complexity.subtract(shift), MATH).multiply(scale, MATH);
    }

    public BigDecimal bandLiftedFloorAp(BigDecimal existingAp, BigDecimal complexity, Curve scoreCurve,
            MissionBand band) {
        if (existingAp == null || existingAp.signum() <= 0) {
            return null;
        }
        BigDecimal existingNormalized = targetNormalizedAp(existingAp, complexity, scoreCurve);
        if (existingNormalized == null) {
            return existingAp.add(BigDecimal.ONE);
        }
        BigDecimal absoluteStep = switch (band) {
            case easy -> new BigDecimal("0.015");
            case medium -> new BigDecimal("0.030");
            case hard -> new BigDecimal("0.055");
            case extreme -> new BigDecimal("0.090");
        };
        BigDecimal headroomFraction = switch (band) {
            case easy -> new BigDecimal("0.15");
            case medium -> new BigDecimal("0.30");
            case hard -> new BigDecimal("0.50");
            case extreme -> new BigDecimal("0.75");
        };
        BigDecimal headroom = BigDecimal.ONE.subtract(existingNormalized).max(BigDecimal.ZERO);
        BigDecimal step = absoluteStep.min(headroom.multiply(headroomFraction, MATH));
        BigDecimal liftedNormalized = existingNormalized.add(step);
        BigDecimal scale = scoreCurve.getScale() != null ? scoreCurve.getScale() : BigDecimal.ONE;
        BigDecimal shift = scoreCurve.getShift() != null ? scoreCurve.getShift() : BigDecimal.ZERO;
        return liftedNormalized.multiply(complexity.subtract(shift), MATH).multiply(scale, MATH);
    }

    public ComplexityRange complexityRange(BigDecimal threshold, BigDecimal multiplier, Curve scoreCurve) {
        BigDecimal target = targetRawAp(threshold, multiplier);
        if (target == null || target.signum() <= 0) {
            return null;
        }
        BigDecimal scale = scoreCurve.getScale() != null ? scoreCurve.getScale() : BigDecimal.ONE;
        BigDecimal shift = scoreCurve.getShift() != null ? scoreCurve.getShift() : BigDecimal.ZERO;
        BigDecimal minC = target.divide(NORM_AP_MAX.multiply(scale, MATH), MATH).add(shift);
        BigDecimal maxC = target.divide(NORM_AP_MIN.multiply(scale, MATH), MATH).add(shift);
        return new ComplexityRange(minC, maxC);
    }

    public int computeXpReward(MissionTemplate template, BigDecimal skillLevel,
            MissionBand band, BigDecimal snipeDistance) {
        if (template.getXpCurve() == null) {
            return 0;
        }
        BigDecimal lookupX = skillLevel != null ? skillLevel : BigDecimal.ZERO;
        BigDecimal base = apCalculationService.interpolate(template.getXpCurve(), lookupX);
        BigDecimal bandMult = bandMultiplier(template, band);
        BigDecimal snipeBoost = snipeDistance == null
                ? BigDecimal.ONE
                : BigDecimal.ONE.add(snipeDistance.divide(SNIPE_BOOST_DIVISOR, MATH).min(SNIPE_BOOST_CAP));
        BigDecimal result = base
                .multiply(template.getXpMultiplier(), MATH)
                .multiply(bandMult, MATH)
                .multiply(snipeBoost, MATH);
        return result.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private BigDecimal peakAccuracyClamp(BigDecimal peakRawAp, BigDecimal complexity, Curve scoreCurve) {
        if (peakRawAp == null || peakRawAp.signum() <= 0) {
            return ACC_CEILING;
        }
        BigDecimal normalized = targetNormalizedAp(peakRawAp, complexity, scoreCurve);
        if (normalized == null) {
            return ACC_CEILING;
        }
        BigDecimal acc = apCalculationService.inverseInterpolate(scoreCurve, normalized.min(BigDecimal.ONE));
        return acc.add(CEILING_EPSILON).min(ACC_CEILING);
    }

    public record ComplexityRange(BigDecimal min, BigDecimal max) {
    }
}
