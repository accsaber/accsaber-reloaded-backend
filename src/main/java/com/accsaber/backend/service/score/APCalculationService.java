package com.accsaber.backend.service.score;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
import com.accsaber.backend.util.Rounding;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class APCalculationService {

    private static final Logger log = LoggerFactory.getLogger(APCalculationService.class);
    private static final int AP_SCALE = 6;
    private static final int ACCURACY_SCALE = 10;

    private final CurvePointRepository curvePointRepository;
    private final Map<UUID, CurveTable> curveCache = new ConcurrentHashMap<>();
    private final Map<UUID, WeightParams> weightCache = new ConcurrentHashMap<>();

    private record CurveTable(double[] xs, double[] ys) {

        double interpolate(double x) {
            int idx = Arrays.binarySearch(xs, x);
            if (idx >= 0) {
                return ys[idx];
            }
            int ip = -idx - 1;
            if (ip == 0) {
                return ys[0];
            }
            if (ip == xs.length) {
                return ys[xs.length - 1];
            }
            double x0 = xs[ip - 1];
            double x1 = xs[ip];
            double y0 = ys[ip - 1];
            double y1 = ys[ip];
            return y0 + (x - x0) * (y1 - y0) / (x1 - x0);
        }

        double inverseInterpolate(double y) {
            for (int i = 0; i < ys.length; i++) {
                if (y <= ys[i]) {
                    if (i == 0) {
                        return xs[0];
                    }
                    double dy = ys[i] - ys[i - 1];
                    if (dy == 0.0) {
                        return xs[i];
                    }
                    return xs[i - 1] + (xs[i] - xs[i - 1]) * ((y - ys[i - 1]) / dy);
                }
            }
            return xs.length == 0 ? 0.0 : xs[xs.length - 1];
        }
    }

    private record WeightParams(double k, double numerator, double x0) {

        double weightAt(int position) {
            return numerator / (1 + Math.exp(k * (position - x0)));
        }
    }

    public static Double computeAccuracy(Integer score, Integer maxScore) {
        return Rounding.round((double) score / maxScore, ACCURACY_SCALE);
    }

    public APResult calculateRawAP(double accuracy, double complexity, Curve scoreCurve) {
        double normalizedAP = interpolate(scoreCurve, accuracy);
        double rawAP = Rounding.round(
                normalizedAP * (complexity - scoreCurve.getShift()) * scoreCurve.getScale(), AP_SCALE);
        return new APResult(rawAP, normalizedAP);
    }

    public double calculateWeightedAP(double rawAP, int position, Curve weightCurve) {
        return Rounding.round(rawAP * positionWeight(position, weightCurve), AP_SCALE);
    }

    public double calculateRawApForOneWeightedGain(List<Double> sortedRawApsDesc, Curve weightCurve) {
        List<Double> sorted = sortedRawApsDesc == null ? List.of() : sortedRawApsDesc;
        WeightParams params = getOrLoadWeightParams(weightCurve);
        double target = totalWeightedAP(sorted, params) + 1.0;

        double lo = 0.0;
        double hi = sorted.isEmpty()
                ? 100.0
                : Math.max(100.0, sorted.get(0) * 3.0);

        for (int i = 0; i < 60; i++) {
            double mid = (lo + hi) / 2.0;
            if (totalWeightedAPWithInsert(sorted, mid, params) < target) {
                lo = mid;
            } else {
                hi = mid;
            }
            if (hi - lo < 1e-4) {
                break;
            }
        }
        return Rounding.round((lo + hi) / 2.0, AP_SCALE);
    }

    private double totalWeightedAP(List<Double> sortedRawApsDesc, WeightParams params) {
        double total = 0.0;
        for (int i = 0; i < sortedRawApsDesc.size(); i++) {
            total += sortedRawApsDesc.get(i) * params.weightAt(i);
        }
        return total;
    }

    private double totalWeightedAPWithInsert(List<Double> sortedRawApsDesc, double newRaw, WeightParams params) {
        int insertAt = sortedRawApsDesc.size();
        for (int i = 0; i < sortedRawApsDesc.size(); i++) {
            if (newRaw > sortedRawApsDesc.get(i)) {
                insertAt = i;
                break;
            }
        }
        double total = 0.0;
        int pos = 0;
        for (int i = 0; i < insertAt; i++) {
            total += sortedRawApsDesc.get(i) * params.weightAt(pos++);
        }
        total += newRaw * params.weightAt(pos++);
        for (int i = insertAt; i < sortedRawApsDesc.size(); i++) {
            total += sortedRawApsDesc.get(i) * params.weightAt(pos++);
        }
        return total;
    }

    private double positionWeight(int position, Curve weightCurve) {
        return getOrLoadWeightParams(weightCurve).weightAt(position);
    }

    private WeightParams getOrLoadWeightParams(Curve weightCurve) {
        return weightCache.computeIfAbsent(weightCurve.getId(), id -> {
            double k = weightCurve.getXParameterValue();
            double y1 = weightCurve.getYParameterValue();
            double x1 = weightCurve.getZParameterValue();
            double x0 = -Math.log((1 - y1) / (y1 * Math.exp(k * x1) - 1)) / k;
            return new WeightParams(k, 1 + Math.exp(-k * x0), x0);
        });
    }

    public double inverseInterpolate(Curve curve, double normalizedAP) {
        if (curve.getType() != CurveType.POINT_LOOKUP) {
            throw new IllegalArgumentException(
                    "Cannot inverse-interpolate a FORMULA type curve");
        }
        CurveTable table = getOrLoadPoints(curve.getId());
        if (table.xs().length == 0) {
            throw new IllegalStateException(
                    "No curve points loaded for curve: " + curve.getId());
        }
        return table.inverseInterpolate(normalizedAP);
    }

    public double interpolate(Curve curve, double accuracy) {
        if (curve.getType() != CurveType.POINT_LOOKUP) {
            throw new IllegalArgumentException(
                    "Cannot interpolate a FORMULA type curve; use calculateWeightedAP instead");
        }
        return interpolate(curve.getId(), accuracy);
    }

    public double interpolate(UUID curveId, double x) {
        CurveTable table = getOrLoadPoints(curveId);
        if (table.xs().length == 0) {
            throw new IllegalStateException(
                    "No curve points loaded for curve: " + curveId);
        }
        return table.interpolate(x);
    }

    private CurveTable getOrLoadPoints(UUID curveId) {
        return curveCache.computeIfAbsent(curveId, id -> {
            log.info("Loading curve points for curve: {}", id);
            List<CurvePoint> points = curvePointRepository.findByCurveIdOrderByXAsc(id);
            double[] xs = new double[points.size()];
            double[] ys = new double[points.size()];
            for (int i = 0; i < points.size(); i++) {
                xs[i] = points.get(i).getX();
                ys[i] = points.get(i).getY();
            }
            log.info("Loaded {} curve points for curve: {}", xs.length, id);
            return new CurveTable(xs, ys);
        });
    }

    public void evictCurveCache(UUID curveId) {
        curveCache.remove(curveId);
        weightCache.remove(curveId);
        log.info("Evicted curve cache for curve: {}", curveId);
    }

    public void evictAllCurveCaches() {
        curveCache.clear();
        weightCache.clear();
        log.info("Evicted all curve caches");
    }
}
