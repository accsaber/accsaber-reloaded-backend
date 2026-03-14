package com.accsaber.backend.service.score;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.dto.APResult;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.CurvePoint;
import com.accsaber.backend.model.entity.CurveType;
import com.accsaber.backend.repository.CurvePointRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class APCalculationService {

    private static final Logger log = LoggerFactory.getLogger(APCalculationService.class);
    private static final MathContext MATH_CONTEXT = new MathContext(16, RoundingMode.HALF_UP);
    private static final int AP_SCALE = 6;

    private final CurvePointRepository curvePointRepository;
    private final Map<UUID, TreeMap<BigDecimal, BigDecimal>> curveCache = new ConcurrentHashMap<>();

    public APResult calculateRawAP(BigDecimal accuracy, BigDecimal complexity, Curve scoreCurve) {
        BigDecimal normalizedAP = interpolate(scoreCurve, accuracy);
        BigDecimal rawAP = normalizedAP
                .multiply(complexity.subtract(scoreCurve.getShift()), MATH_CONTEXT)
                .multiply(scoreCurve.getScale(), MATH_CONTEXT)
                .setScale(AP_SCALE, RoundingMode.HALF_UP);
        return new APResult(rawAP, normalizedAP);
    }

    public BigDecimal calculateWeightedAP(BigDecimal rawAP, int position, Curve weightCurve) {
        double k = weightCurve.getXParameterValue().doubleValue();
        double y1 = weightCurve.getYParameterValue().doubleValue();
        double x1 = weightCurve.getZParameterValue().doubleValue();
        double x0 = -Math.log((1 - y1) / (y1 * Math.exp(k * x1) - 1)) / k;

        double x = position;
        double weight = (1 + Math.exp(-k * x0)) / (1 + Math.exp(k * (x - x0)));

        BigDecimal weightBD = new BigDecimal(weight, MATH_CONTEXT);
        return rawAP.multiply(weightBD, MATH_CONTEXT)
                .setScale(AP_SCALE, RoundingMode.HALF_UP);
    }

    BigDecimal interpolate(Curve curve, BigDecimal accuracy) {
        if (curve.getType() != CurveType.POINT_LOOKUP) {
            throw new IllegalArgumentException(
                    "Cannot interpolate a FORMULA type curve; use calculateWeightedAP instead");
        }

        TreeMap<BigDecimal, BigDecimal> points = getOrLoadPoints(curve.getId());

        if (points.isEmpty()) {
            throw new IllegalStateException(
                    "No curve points loaded for curve: " + curve.getId());
        }

        Map.Entry<BigDecimal, BigDecimal> floor = points.floorEntry(accuracy);
        Map.Entry<BigDecimal, BigDecimal> ceiling = points.ceilingEntry(accuracy);

        if (floor == null && ceiling == null) {
            return BigDecimal.ZERO;
        }
        if (floor == null) {
            return ceiling.getValue();
        }
        if (ceiling == null) {
            return floor.getValue();
        }
        if (floor.getKey().compareTo(ceiling.getKey()) == 0) {
            return floor.getValue();
        }

        // y = y0 + (x - x0) * (y1 - y0) / (x1 - x0)
        BigDecimal x0 = floor.getKey();
        BigDecimal y0 = floor.getValue();
        BigDecimal x1 = ceiling.getKey();
        BigDecimal y1 = ceiling.getValue();

        BigDecimal dx = accuracy.subtract(x0);
        BigDecimal range = x1.subtract(x0);
        BigDecimal dy = y1.subtract(y0);

        return y0.add(dx.multiply(dy, MATH_CONTEXT).divide(range, MATH_CONTEXT));
    }

    private TreeMap<BigDecimal, BigDecimal> getOrLoadPoints(UUID curveId) {
        return curveCache.computeIfAbsent(curveId, id -> {
            log.info("Loading curve points for curve: {}", id);
            List<CurvePoint> points = curvePointRepository.findByCurveIdOrderByXAsc(id);
            TreeMap<BigDecimal, BigDecimal> map = new TreeMap<>();
            for (CurvePoint point : points) {
                map.put(point.getX(), point.getY());
            }
            log.info("Loaded {} curve points for curve: {}", map.size(), id);
            return map;
        });
    }

    public void evictCurveCache(UUID curveId) {
        curveCache.remove(curveId);
        log.info("Evicted curve cache for curve: {}", curveId);
    }

    public void evictAllCurveCaches() {
        curveCache.clear();
        log.info("Evicted all curve caches");
    }
}
