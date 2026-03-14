package com.accsaber.backend.service.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.CurveType;
import com.accsaber.backend.repository.CurveRepository;

@ExtendWith(MockitoExtension.class)
class XPCalculationServiceTest {

    @Mock
    private APCalculationService apCalculationService;

    @Mock
    private CurveRepository curveRepository;

    @InjectMocks
    private XPCalculationService service;

    private static final UUID XP_CURVE_ID = UUID.fromString("acc00000-0000-0000-0000-000000000003");
    private static final BigDecimal COMPLEXITY_10 = BigDecimal.valueOf(10);
    private static final BigDecimal COMPLEXITY_12 = BigDecimal.valueOf(12);
    private Curve xpCurve;

    @BeforeEach
    void setUp() {
        service.evictXpCurveCache();

        xpCurve = Curve.builder()
                .id(XP_CURVE_ID)
                .name("XP Curve")
                .type(CurveType.POINT_LOOKUP)
                .build();

        ReflectionTestUtils.setField(service, "baseXpPerScore", 25);
        ReflectionTestUtils.setField(service, "maxBonusXpPerScore", 1000);
        ReflectionTestUtils.setField(service, "improvementMultiplier", 1.5);

        org.mockito.Mockito.clearInvocations(apCalculationService);
    }

    @Nested
    class CalculateXpForNewMap {

        @Test
        void baseXpAlwaysIncluded() {
            when(curveRepository.findById(XP_CURVE_ID)).thenReturn(Optional.of(xpCurve));
            when(apCalculationService.interpolate(xpCurve, BigDecimal.ZERO))
                    .thenReturn(BigDecimal.ZERO);

            BigDecimal result = service.calculateXpForNewMap(BigDecimal.ZERO, COMPLEXITY_10);

            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(25));
        }

        @Test
        void topAccuracy_givesBaseXpPlusMaxBonus() {
            when(curveRepository.findById(XP_CURVE_ID)).thenReturn(Optional.of(xpCurve));
            when(apCalculationService.interpolate(xpCurve, BigDecimal.ONE))
                    .thenReturn(BigDecimal.ONE);

            BigDecimal result = service.calculateXpForNewMap(BigDecimal.ONE, COMPLEXITY_10);

            // 25 + 1.0 * 1000 * (10/10) = 1025
            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(1025));
        }

        @Test
        void complexityMultiplier_scalesBonus() {
            when(curveRepository.findById(XP_CURVE_ID)).thenReturn(Optional.of(xpCurve));
            when(apCalculationService.interpolate(xpCurve, new BigDecimal("0.95")))
                    .thenReturn(new BigDecimal("0.18"));

            BigDecimal result = service.calculateXpForNewMap(new BigDecimal("0.95"), COMPLEXITY_12);

            // 25 + 0.18 * 1000 * (12/10) = 25 + 216 = 241
            assertThat(result.doubleValue()).isCloseTo(241.0, within(1.0));
        }

        @Test
        void midAccuracy_returnsCorrectXp() {
            when(curveRepository.findById(XP_CURVE_ID)).thenReturn(Optional.of(xpCurve));
            when(apCalculationService.interpolate(xpCurve, new BigDecimal("0.97")))
                    .thenReturn(new BigDecimal("0.36"));

            BigDecimal result = service.calculateXpForNewMap(new BigDecimal("0.97"), COMPLEXITY_10);

            // 25 + 0.36 * 1000 * 1.0 = 25 + 360 = 385
            assertThat(result.doubleValue()).isCloseTo(385.0, within(1.0));
        }
    }

    @Nested
    class CalculateXpForImprovement {

        @Test
        void improvement_getsBaseXpPlusBoostedDelta() {
            // Old score earned: 25 (base) + 180 (curve bonus) = 205
            // New accuracy 0.99 → normalizedXP = 0.78 → bonus = 780
            // delta = 780 - 180 = 600 → boosted = 600 * 1.5 = 900
            // total = 25 + 900 = 925
            when(curveRepository.findById(XP_CURVE_ID)).thenReturn(Optional.of(xpCurve));
            when(apCalculationService.interpolate(xpCurve, new BigDecimal("0.99")))
                    .thenReturn(new BigDecimal("0.78"));

            BigDecimal oldXpGained = BigDecimal.valueOf(205); // base=25, curveBonus=180
            BigDecimal result = service.calculateXpForImprovement(
                    new BigDecimal("0.99"), COMPLEXITY_10, oldXpGained);

            // newCurveBonus = 0.78 * 1000 * 1.0 = 780
            // oldCurveBonus = 205 - 25 = 180
            // delta = 780 - 180 = 600
            // boostedDelta = 600 * 1.5 = 900
            // total = 25 + 900 = 925
            assertThat(result.doubleValue()).isCloseTo(925.0, within(1.0));
        }

        @Test
        void improvement_whenNewCurveBelowOld_deltaIsZero_getsOnlyBaseXp() {
            when(curveRepository.findById(XP_CURVE_ID)).thenReturn(Optional.of(xpCurve));
            when(apCalculationService.interpolate(xpCurve, new BigDecimal("0.90")))
                    .thenReturn(new BigDecimal("0.01"));

            BigDecimal oldXpGained = BigDecimal.valueOf(60);
            BigDecimal result = service.calculateXpForImprovement(
                    new BigDecimal("0.90"), COMPLEXITY_10, oldXpGained);

            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(25));
        }

        @Test
        void improvement_withNullExistingXp_treatsOldCurveBonusAsZero() {
            when(curveRepository.findById(XP_CURVE_ID)).thenReturn(Optional.of(xpCurve));
            when(apCalculationService.interpolate(xpCurve, new BigDecimal("0.95")))
                    .thenReturn(new BigDecimal("0.18"));

            BigDecimal result = service.calculateXpForImprovement(
                    new BigDecimal("0.95"), COMPLEXITY_10, null);

            // curveBonus = 0.18 * 1000 * 1.0 = 180
            // delta = 180 - 0 = 180, boosted = 180 * 1.5 = 270
            // total = 25 + 270 = 295
            assertThat(result.doubleValue()).isCloseTo(295.0, within(1.0));
        }
    }

    @Nested
    class CalculateXpForWorseScore {

        @Test
        void returnsOnlyBaseXp() {
            BigDecimal result = service.calculateXpForWorseScore();

            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(25));
        }

        @Test
        void doesNotCallCurveOrApService() {
            service.calculateXpForWorseScore();

            verify(curveRepository, times(0)).findById(any());
            verify(apCalculationService, times(0)).interpolate(any(), any());
        }
    }

    @Nested
    class CurveCaching {

        @Test
        void xpCurveLoadedOnce_cachedOnSubsequentCalls() {
            when(curveRepository.findById(XP_CURVE_ID)).thenReturn(Optional.of(xpCurve));
            when(apCalculationService.interpolate(any(), any())).thenReturn(BigDecimal.ZERO);

            service.calculateXpForNewMap(BigDecimal.ZERO, COMPLEXITY_10);
            service.calculateXpForNewMap(BigDecimal.ZERO, COMPLEXITY_10);
            service.calculateXpForNewMap(BigDecimal.ZERO, COMPLEXITY_10);

            verify(curveRepository, times(1)).findById(XP_CURVE_ID);
        }

        @Test
        void evictCache_forcesReloadOnNextCall() {
            when(curveRepository.findById(XP_CURVE_ID)).thenReturn(Optional.of(xpCurve));
            when(apCalculationService.interpolate(any(), any())).thenReturn(BigDecimal.ZERO);

            service.calculateXpForNewMap(BigDecimal.ZERO, COMPLEXITY_10);
            service.evictXpCurveCache();
            service.calculateXpForNewMap(BigDecimal.ZERO, COMPLEXITY_10);

            verify(curveRepository, times(2)).findById(XP_CURVE_ID);
        }

        @Test
        void evictCache_alsoClearsApServiceCache() {
            service.evictXpCurveCache();

            verify(apCalculationService).evictCurveCache(XP_CURVE_ID);
        }
    }

    @Nested
    class ComputeCurveBonus {

        @Test
        void halfNormalizedXp_withReferenceComplexity_returnsHalfOfMax() {
            when(curveRepository.findById(XP_CURVE_ID)).thenReturn(Optional.of(xpCurve));
            when(apCalculationService.interpolate(xpCurve, new BigDecimal("0.95")))
                    .thenReturn(new BigDecimal("0.5"));

            BigDecimal bonus = service.computeCurveBonus(new BigDecimal("0.95"), COMPLEXITY_10);

            // 0.5 * 1000 * (10/10) = 500
            assertThat(bonus.doubleValue()).isCloseTo(500.0, within(0.001));
        }

        @Test
        void complexityAboveReference_scalesUp() {
            when(curveRepository.findById(XP_CURVE_ID)).thenReturn(Optional.of(xpCurve));
            when(apCalculationService.interpolate(xpCurve, new BigDecimal("0.95")))
                    .thenReturn(new BigDecimal("0.5"));

            BigDecimal bonus = service.computeCurveBonus(new BigDecimal("0.95"), COMPLEXITY_12);

            // 0.5 * 1000 * (12/10) = 600
            assertThat(bonus.doubleValue()).isCloseTo(600.0, within(0.001));
        }
    }

    @Nested
    class Config {

        @Test
        void getBaseXpPerScore_returnsConfiguredValue() {
            assertThat(service.getBaseXpPerScore()).isEqualTo(25);
        }

        @Test
        void getMaxBonusXpPerScore_returnsConfiguredValue() {
            assertThat(service.getMaxBonusXpPerScore()).isEqualTo(1000);
        }

        @Test
        void getXpCurveId_returnsKnownId() {
            assertThat(service.getXpCurveId())
                    .isEqualTo(UUID.fromString("acc00000-0000-0000-0000-000000000003"));
        }
    }
}
