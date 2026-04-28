package com.accsaber.backend.service.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.dto.APResult;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.CurvePoint;
import com.accsaber.backend.model.entity.CurveType;
import com.accsaber.backend.repository.CurvePointRepository;

@ExtendWith(MockitoExtension.class)
class APCalculationServiceTest {

        @Mock
        private CurvePointRepository curvePointRepository;

        @InjectMocks
        private APCalculationService apCalculationService;

        private Curve scoreCurve;
        private Curve weightCurve;

        @BeforeEach
        void setUp() {
                apCalculationService.evictAllCurveCaches();

                scoreCurve = Curve.builder()
                                .id(UUID.randomUUID())
                                .name("Test Score Curve")
                                .type(CurveType.POINT_LOOKUP)
                                .scale(new BigDecimal("61"))
                                .shift(new BigDecimal("-18"))
                                .build();

                weightCurve = Curve.builder()
                                .id(UUID.randomUUID())
                                .name("Test Weight Curve")
                                .type(CurveType.FORMULA)
                                .formula("LOGISTIC_SIGMOID")
                                .xParameterName("k")
                                .xParameterValue(new BigDecimal("0.4"))
                                .yParameterName("y1")
                                .yParameterValue(new BigDecimal("0.1"))
                                .zParameterName("x1")
                                .zParameterValue(new BigDecimal("15"))
                                .build();
        }

        private CurvePoint point(BigDecimal x, BigDecimal y) {
                return CurvePoint.builder()
                                .id(UUID.randomUUID())
                                .curve(scoreCurve)
                                .x(x)
                                .y(y)
                                .build();
        }

        @Nested
        class Interpolation {

                @Test
                void exactPointHit_returnsExactValue() {
                        List<CurvePoint> points = List.of(
                                        point(new BigDecimal("0.0"), new BigDecimal("0.0")),
                                        point(new BigDecimal("0.5"), new BigDecimal("0.3")),
                                        point(new BigDecimal("1.0"), new BigDecimal("1.0")));
                        when(curvePointRepository.findByCurveIdOrderByXAsc(scoreCurve.getId()))
                                        .thenReturn(points);

                        BigDecimal result = apCalculationService.interpolate(scoreCurve, new BigDecimal("0.5"));

                        assertThat(result).isEqualByComparingTo(new BigDecimal("0.3"));
                }

                @Test
                void interpolatesBetweenPoints() {
                        List<CurvePoint> points = List.of(
                                        point(new BigDecimal("0.0"), new BigDecimal("0.0")),
                                        point(new BigDecimal("1.0"), new BigDecimal("1.0")));
                        when(curvePointRepository.findByCurveIdOrderByXAsc(scoreCurve.getId()))
                                        .thenReturn(points);

                        BigDecimal result = apCalculationService.interpolate(scoreCurve, new BigDecimal("0.25"));

                        assertThat(result.doubleValue()).isCloseTo(0.25, within(0.0001));
                }

                @Test
                void interpolatesBetweenNonLinearPoints() {
                        List<CurvePoint> points = List.of(
                                        point(new BigDecimal("0.90"), new BigDecimal("0.40")),
                                        point(new BigDecimal("0.95"), new BigDecimal("0.60")));
                        when(curvePointRepository.findByCurveIdOrderByXAsc(scoreCurve.getId()))
                                        .thenReturn(points);

                        // Midpoint between 0.90 and 0.95 should give midpoint between 0.40 and 0.60
                        BigDecimal result = apCalculationService.interpolate(scoreCurve, new BigDecimal("0.925"));

                        assertThat(result.doubleValue()).isCloseTo(0.50, within(0.0001));
                }

                @Test
                void aboveHighestPoint_returnsHighestValue() {
                        List<CurvePoint> points = List.of(
                                        point(new BigDecimal("0.0"), new BigDecimal("0.0")),
                                        point(new BigDecimal("0.99"), new BigDecimal("0.95")));
                        when(curvePointRepository.findByCurveIdOrderByXAsc(scoreCurve.getId()))
                                        .thenReturn(points);

                        BigDecimal result = apCalculationService.interpolate(scoreCurve, new BigDecimal("1.0"));

                        assertThat(result).isEqualByComparingTo(new BigDecimal("0.95"));
                }

                @Test
                void emptyPoints_throwsIllegalState() {
                        when(curvePointRepository.findByCurveIdOrderByXAsc(scoreCurve.getId()))
                                        .thenReturn(Collections.emptyList());

                        assertThatThrownBy(() -> apCalculationService.interpolate(scoreCurve, BigDecimal.ZERO))
                                        .isInstanceOf(IllegalStateException.class)
                                        .hasMessageContaining("No curve points loaded");
                }

                @Test
                void formulaCurve_throwsIllegalArgument() {
                        assertThatThrownBy(() -> apCalculationService.interpolate(weightCurve, BigDecimal.ZERO))
                                        .isInstanceOf(IllegalArgumentException.class)
                                        .hasMessageContaining("Cannot interpolate a FORMULA type curve");
                }
        }

        @Nested
        class RawAPCalculation {

                @Test
                void calculatesRawAP_withComplexityScaling() {
                        List<CurvePoint> points = List.of(
                                        point(new BigDecimal("0.0"), new BigDecimal("0.0")),
                                        point(new BigDecimal("0.95"), new BigDecimal("0.60")),
                                        point(new BigDecimal("1.0"), new BigDecimal("1.0")));
                        when(curvePointRepository.findByCurveIdOrderByXAsc(scoreCurve.getId()))
                                        .thenReturn(points);

                        BigDecimal complexity = new BigDecimal("10.5");
                        BigDecimal accuracy = new BigDecimal("0.95");

                        APResult result = apCalculationService.calculateRawAP(accuracy, complexity, scoreCurve);

                        // curveMultiplier=0.60, complexity=10.5, shift=-18, scale=61
                        // 0.60 * (10.5 - (-18)) * 61 = 0.60 * 28.5 * 61 = 1043.1
                        assertThat(result.rawAP().doubleValue()).isCloseTo(1043.1, within(0.001));
                        assertThat(result.normalizedAP()).isEqualByComparingTo(new BigDecimal("0.60"));
                }

                @Test
                void zeroAccuracy_givesNearZeroAP() {
                        List<CurvePoint> points = List.of(
                                        point(new BigDecimal("0.0"), new BigDecimal("0.0")),
                                        point(new BigDecimal("1.0"), new BigDecimal("1.0")));
                        when(curvePointRepository.findByCurveIdOrderByXAsc(scoreCurve.getId()))
                                        .thenReturn(points);

                        APResult result = apCalculationService.calculateRawAP(
                                        BigDecimal.ZERO, new BigDecimal("10"), scoreCurve);

                        assertThat(result.rawAP().doubleValue()).isCloseTo(0.0, within(0.001));
                }

                @Test
                void higherComplexity_givesHigherAP() {
                        List<CurvePoint> points = List.of(
                                        point(new BigDecimal("0.0"), new BigDecimal("0.0")),
                                        point(new BigDecimal("1.0"), new BigDecimal("1.0")));
                        when(curvePointRepository.findByCurveIdOrderByXAsc(scoreCurve.getId()))
                                        .thenReturn(points);

                        BigDecimal accuracy = new BigDecimal("0.90");
                        BigDecimal lowComplexity = new BigDecimal("5.0");
                        BigDecimal highComplexity = new BigDecimal("15.0");

                        APResult lowResult = apCalculationService.calculateRawAP(accuracy, lowComplexity, scoreCurve);
                        APResult highResult = apCalculationService.calculateRawAP(accuracy, highComplexity, scoreCurve);

                        assertThat(highResult.rawAP()).isGreaterThan(lowResult.rawAP());
                }
        }

        @Nested
        class WeightedAPCalculation {

                @Test
                void position1_givesNearFullAP() {
                        BigDecimal rawAP = new BigDecimal("100.0");

                        BigDecimal weightedAP = apCalculationService.calculateWeightedAP(rawAP, 1, weightCurve);

                        assertThat(weightedAP.doubleValue()).isCloseTo(98.912, within(0.01));
                }

                @Test
                void position2_appliesSigmoidDecay() {
                        BigDecimal rawAP = new BigDecimal("100.0");

                        BigDecimal weightedAP = apCalculationService.calculateWeightedAP(rawAP, 2, weightCurve);

                        assertThat(weightedAP.doubleValue()).isCloseTo(97.332, within(0.01));
                }

                @Test
                void higherPosition_givesLowerWeight() {
                        BigDecimal rawAP = new BigDecimal("100.0");

                        BigDecimal pos1 = apCalculationService.calculateWeightedAP(rawAP, 1, weightCurve);
                        BigDecimal pos5 = apCalculationService.calculateWeightedAP(rawAP, 5, weightCurve);
                        BigDecimal pos10 = apCalculationService.calculateWeightedAP(rawAP, 10, weightCurve);

                        assertThat(pos1).isGreaterThan(pos5);
                        assertThat(pos5).isGreaterThan(pos10);
                }

                @Test
                void position10_matchesExpectedSigmoid() {
                        BigDecimal rawAP = new BigDecimal("100.0");

                        BigDecimal weightedAP = apCalculationService.calculateWeightedAP(rawAP, 10, weightCurve);

                        assertThat(weightedAP.doubleValue()).isCloseTo(45.48, within(0.1));
                }

                @Test
                void position15_matchesTargetWeight() {
                        BigDecimal rawAP = new BigDecimal("100.0");

                        BigDecimal weightedAP = apCalculationService.calculateWeightedAP(rawAP, 15, weightCurve);

                        assertThat(weightedAP.doubleValue()).isCloseTo(10.0, within(0.01));
                }
        }

        @Nested
        class CacheManagement {

                @Test
                void cachesPointsAfterFirstLoad() {
                        List<CurvePoint> points = List.of(
                                        point(new BigDecimal("0.0"), new BigDecimal("0.0")),
                                        point(new BigDecimal("1.0"), new BigDecimal("1.0")));
                        when(curvePointRepository.findByCurveIdOrderByXAsc(scoreCurve.getId()))
                                        .thenReturn(points);

                        apCalculationService.interpolate(scoreCurve, new BigDecimal("0.5"));
                        apCalculationService.interpolate(scoreCurve, new BigDecimal("0.75"));

                        verify(curvePointRepository, times(1)).findByCurveIdOrderByXAsc(scoreCurve.getId());
                }

                @Test
                void evictCurveCache_forcesReload() {
                        List<CurvePoint> points = List.of(
                                        point(new BigDecimal("0.0"), new BigDecimal("0.0")),
                                        point(new BigDecimal("1.0"), new BigDecimal("1.0")));
                        when(curvePointRepository.findByCurveIdOrderByXAsc(scoreCurve.getId()))
                                        .thenReturn(points);

                        apCalculationService.interpolate(scoreCurve, new BigDecimal("0.5"));
                        apCalculationService.evictCurveCache(scoreCurve.getId());

                        List<CurvePoint> updatedPoints = List.of(
                                        point(new BigDecimal("0.0"), new BigDecimal("0.0")),
                                        point(new BigDecimal("1.0"), new BigDecimal("0.8")));
                        when(curvePointRepository.findByCurveIdOrderByXAsc(scoreCurve.getId()))
                                        .thenReturn(updatedPoints);

                        BigDecimal result = apCalculationService.interpolate(scoreCurve, new BigDecimal("1.0"));

                        assertThat(result).isEqualByComparingTo(new BigDecimal("0.8"));
                }
        }

        @Nested
        class CalculateRawApForOneWeightedGain {

                @Test
                void emptyHistoryReturnsTinyRawAp() {
                        BigDecimal result = apCalculationService
                                        .calculateRawApForOneWeightedGain(List.of(), weightCurve);
                        assertThat(result.doubleValue()).isLessThan(2.0).isGreaterThan(0.5);
                }

                @Test
                void singleHighPlayMakesOneGainCheap() {
                        // One play of 1100 raw — top is dominated by it. Adding a small play at
                        // position 2 still gains > 1 weighted easily.
                        BigDecimal result = apCalculationService.calculateRawApForOneWeightedGain(
                                        List.of(new BigDecimal("1100")), weightCurve);
                        assertThat(result.doubleValue()).isLessThan(10.0);
                }

                @Test
                void deepConsistentPlayerNeedsHighRawToGainOne() {
                        // 50 plays clustered near 1000 raw — to gain 1 weighted you need
                        // a play comparable to the top.
                        List<BigDecimal> plays = new java.util.ArrayList<>();
                        for (int i = 0; i < 50; i++) {
                                plays.add(new BigDecimal(1000 - i));
                        }
                        BigDecimal result = apCalculationService
                                        .calculateRawApForOneWeightedGain(plays, weightCurve);
                        assertThat(result.doubleValue()).isGreaterThan(700.0);
                }

                @Test
                void monotonicInPlayCount_higherCountMeansHigherRawNeeded() {
                        List<BigDecimal> few = List.of(
                                        new BigDecimal("900"), new BigDecimal("800"), new BigDecimal("700"));
                        List<BigDecimal> many = new java.util.ArrayList<>();
                        for (int i = 0; i < 30; i++) {
                                many.add(new BigDecimal(900 - i * 5));
                        }
                        BigDecimal fewResult = apCalculationService
                                        .calculateRawApForOneWeightedGain(few, weightCurve);
                        BigDecimal manyResult = apCalculationService
                                        .calculateRawApForOneWeightedGain(many, weightCurve);
                        assertThat(manyResult.doubleValue()).isGreaterThan(fewResult.doubleValue());
                }

                @Test
                void resultIsConsistentWithTotalDelta() {
                        // Sanity: applying the returned raw should add ~1 weighted to the total
                        List<BigDecimal> plays = List.of(
                                        new BigDecimal("1000"), new BigDecimal("950"),
                                        new BigDecimal("900"), new BigDecimal("850"));
                        double before = totalWeighted(plays);
                        BigDecimal raw = apCalculationService
                                        .calculateRawApForOneWeightedGain(plays, weightCurve);
                        List<BigDecimal> after = new java.util.ArrayList<>(plays);
                        insertSorted(after, raw);
                        double afterTotal = totalWeighted(after);
                        assertThat(afterTotal - before).isCloseTo(1.0, within(0.01));
                }

                private double totalWeighted(List<BigDecimal> sortedDesc) {
                        double total = 0;
                        for (int i = 0; i < sortedDesc.size(); i++) {
                                total += sortedDesc.get(i).doubleValue()
                                                * apCalculationService
                                                                .calculateWeightedAP(BigDecimal.ONE, i + 1, weightCurve)
                                                                .doubleValue();
                        }
                        return total;
                }

                private void insertSorted(List<BigDecimal> list, BigDecimal value) {
                        int i = 0;
                        while (i < list.size() && list.get(i).compareTo(value) >= 0) {
                                i++;
                        }
                        list.add(i, value);
                }
        }
}
