package com.accsaber.backend.service.score;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.repository.CurveRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class XPCalculationService {

    private static final UUID XP_CURVE_ID = UUID.fromString("acc00000-0000-0000-0000-000000000003");
    private static final int XP_SCALE = 6;
    private static final BigDecimal REFERENCE_COMPLEXITY = BigDecimal.valueOf(10);
    private static final BigDecimal MIN_COMPLEXITY = new BigDecimal("4.5");

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

    public BigDecimal calculateXpForNewMap(BigDecimal accuracy, BigDecimal complexity) {
        BigDecimal curveBonus = computeCurveBonus(accuracy, complexity);
        return BigDecimal.valueOf(baseXpPerScore).add(curveBonus)
                .setScale(XP_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateXpForImprovement(BigDecimal newAccuracy, BigDecimal oldAccuracy,
            BigDecimal complexity) {
        BigDecimal curveBonus = computeCurveBonus(newAccuracy, complexity);
        BigDecimal oldCurveBonus = oldAccuracy != null
                ? computeCurveBonus(oldAccuracy, complexity)
                : BigDecimal.ZERO;
        BigDecimal curveDelta = curveBonus.subtract(oldCurveBonus).max(BigDecimal.ZERO);
        BigDecimal boostedDelta = curveDelta.multiply(BigDecimal.valueOf(improvementMultiplier));
        return BigDecimal.valueOf(baseXpPerScore).add(boostedDelta)
                .setScale(XP_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateXpForWorseScore() {
        return BigDecimal.valueOf(baseXpPerScore).setScale(XP_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal computeCurveBonus(BigDecimal accuracy, BigDecimal complexity) {
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
        BigDecimal normalizedXP = apCalculationService.interpolate(curve, accuracy);
        BigDecimal clampedComplexity = complexity.max(MIN_COMPLEXITY);
        double complexityMultiplier = Math.cbrt(clampedComplexity.doubleValue() / REFERENCE_COMPLEXITY.doubleValue());
        return normalizedXP.multiply(BigDecimal.valueOf(maxBonusXpPerScore))
                .multiply(BigDecimal.valueOf(complexityMultiplier))
                .setScale(XP_SCALE, RoundingMode.HALF_UP);
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

    public int getMaxBonusXpPerScore() {
        return maxBonusXpPerScore;
    }
}
