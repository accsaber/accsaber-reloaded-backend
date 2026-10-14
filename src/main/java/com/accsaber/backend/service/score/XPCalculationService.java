package com.accsaber.backend.service.score;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.repository.CurveRepository;
import com.accsaber.backend.util.Rounding;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class XPCalculationService {

    private static final UUID XP_CURVE_ID = UUID.fromString("acc00000-0000-0000-0000-000000000003");
    private static final int XP_SCALE = 6;
    private static final double REFERENCE_COMPLEXITY = 10.0;
    private static final double MIN_COMPLEXITY = 4.5;

    private final APCalculationService apCalculationService;
    private final CurveRepository curveRepository;

    @Value("${accsaber.xp.base-xp-per-score:25}")
    private int baseXpPerScore;

    @Value("${accsaber.xp.max-bonus-xp-per-score:900}")
    private int maxBonusXpPerScore;

    @Value("${accsaber.xp.improvement-multiplier:1.5}")
    private double improvementMultiplier;

    private volatile Curve cachedXpCurve;
    private final Object curveLock = new Object();

    public double calculateXpForNewMap(double accuracy, double complexity) {
        return Rounding.round(baseXpPerScore + computeCurveBonus(accuracy, complexity), XP_SCALE);
    }

    public double calculateXpForImprovement(double newAccuracy, Double oldAccuracy, double complexity) {
        double curveBonus = computeCurveBonus(newAccuracy, complexity);
        double oldCurveBonus = oldAccuracy != null
                ? computeCurveBonus(oldAccuracy, complexity)
                : 0.0;
        double curveDelta = Math.max(curveBonus - oldCurveBonus, 0.0);
        return Rounding.round(baseXpPerScore + curveDelta * improvementMultiplier, XP_SCALE);
    }

    public double calculateXpForWorseScore() {
        return Rounding.round(baseXpPerScore, XP_SCALE);
    }

    public double computeCurveBonus(double accuracy, double complexity) {
        Curve curve = cachedXpCurve;
        if (curve == null) {
            synchronized (curveLock) {
                curve = cachedXpCurve;
                if (curve == null) {
                    curve = curveRepository.findById(XP_CURVE_ID)
                            .orElseThrow(() -> new IllegalStateException("XP curve not found"));
                    cachedXpCurve = curve;
                }
            }
        }
        double normalizedXP = apCalculationService.interpolate(curve, accuracy);
        double clampedComplexity = Math.max(complexity, MIN_COMPLEXITY);
        double complexityMultiplier = Math.cbrt(clampedComplexity / REFERENCE_COMPLEXITY);
        return Rounding.round(normalizedXP * maxBonusXpPerScore * complexityMultiplier, XP_SCALE);
    }

    public void evictXpCurveCache() {
        cachedXpCurve = null;
        apCalculationService.evictCurveCache(XP_CURVE_ID);
    }

    public UUID getXpCurveId() {
        return XP_CURVE_ID;
    }

    public int getBaseXpPerScore() {
        return baseXpPerScore;
    }
}
