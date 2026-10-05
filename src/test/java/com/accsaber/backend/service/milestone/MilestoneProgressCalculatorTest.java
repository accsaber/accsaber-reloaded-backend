package com.accsaber.backend.service.milestone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.milestone.Milestone;
import com.accsaber.backend.model.entity.milestone.MilestoneProgressModel;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.service.score.APCalculationService;

class MilestoneProgressCalculatorTest {

    private static final UUID OVERALL_ID = UUID.randomUUID();
    private static final double TOLERANCE = 0.005;

    private APCalculationService apCalculationService;
    private CategoryRepository categoryRepository;
    private UserCategoryStatisticsRepository statisticsRepository;
    private MilestoneProgressCalculator calculator;

    @BeforeEach
    void setUp() {
        apCalculationService = mock(APCalculationService.class);
        categoryRepository = mock(CategoryRepository.class);
        statisticsRepository = mock(UserCategoryStatisticsRepository.class);
        calculator = new MilestoneProgressCalculator(apCalculationService, categoryRepository, statisticsRepository);
    }

    private Milestone milestone(MilestoneProgressModel model, double target, String comparison) {
        return Milestone.builder()
                .id(UUID.randomUUID())
                .targetValue(target)
                .comparison(comparison)
                .progressModel(model)
                .build();
    }

    private void withPopulation(long players) {
        Category overall = Category.builder().id(OVERALL_ID).code("overall").build();
        lenient().when(categoryRepository.findByCodeAndActiveTrue("overall")).thenReturn(Optional.of(overall));
        lenient().when(statisticsRepository.countActivePlayersInCategory(OVERALL_ID)).thenReturn(players);
    }

    private Curve curve;

    private void withScoreCurve() {
        curve = Curve.builder().id(UUID.randomUUID()).build();
        lenient().when(apCalculationService.interpolate(eq(curve.getId()), anyDouble()))
                .thenAnswer(invocation -> scoreCurve(invocation.getArgument(1)));
    }

    private static double scoreCurve(double accuracy) {
        double[][] points = { { 0.80, 0.05 }, { 0.90, 0.13 }, { 0.935, 0.20 }, { 0.95, 0.28 }, { 0.97, 0.38 },
                { 0.98, 0.47 }, { 0.99, 0.59 }, { 0.995, 0.69 }, { 0.9975, 0.81 }, { 0.9995, 0.97 }, { 1.0, 1.0 } };
        if (accuracy <= points[0][0]) {
            return points[0][1];
        }
        for (int i = 1; i < points.length; i++) {
            if (accuracy <= points[i][0]) {
                double[] low = points[i - 1];
                double[] high = points[i];
                return low[1] + (high[1] - low[1]) * (accuracy - low[0]) / (high[0] - low[0]);
            }
        }
        return 1.0;
    }

    @Nested
    @DisplayName("linear")
    class Linear {

        @Test
        void countsStayProportional() {
            Milestone m = milestone(MilestoneProgressModel.LINEAR, 100, "GTE");

            assertThat(calculator.normalize(m, 50.0)).isEqualTo(0.5);
        }

        @Test
        void anchorsApThresholdsAtTheFloor() {
            Milestone m = milestone(MilestoneProgressModel.LINEAR, 1000, "GTE");
            m.setProgressFloor(500.0);

            assertThat(calculator.normalize(m, 950.0)).isEqualTo(0.9);
            assertThat(calculator.normalize(m, 800.0)).isEqualTo(0.6);
            assertThat(calculator.normalize(m, 700.0)).isEqualTo(0.4);
        }

        @Test
        void readsZeroBelowTheFloorRatherThanNegative() {
            Milestone m = milestone(MilestoneProgressModel.LINEAR, 1000, "GTE");
            m.setProgressFloor(500.0);

            assertThat(calculator.normalize(m, 100.0)).isEqualTo(0.0);
        }

        @Test
        void neverExceedsOne() {
            Milestone m = milestone(MilestoneProgressModel.LINEAR, 100, "GTE");

            assertThat(calculator.normalize(m, 250.0)).isEqualTo(1.0);
        }

        @Test
        void invertsForLowerIsBetterWithoutAFloor() {
            Milestone m = milestone(MilestoneProgressModel.LINEAR, 50, "LTE");

            assertThat(calculator.normalize(m, 100.0)).isEqualTo(0.5);
        }

        @Test
        void nullProgressStaysNull() {
            assertThat(calculator.normalize(milestone(MilestoneProgressModel.LINEAR, 100, "GTE"), null)).isNull();
        }
    }

    @Nested
    @DisplayName("curve")
    class CurveModel {

        @Test
        void accuracyProgressReflectsTheScoreCurve() {
            withScoreCurve();
            Milestone m = milestone(MilestoneProgressModel.CURVE, 0.99, "GTE");
            m.setProgressCurve(curve);

            assertThat(calculator.normalize(m, 0.98)).isCloseTo(0.797, within(TOLERANCE));
            assertThat(calculator.normalize(m, 0.97)).isCloseTo(0.644, within(TOLERANCE));
        }

        @Test
        void fallsBackToLinearWhenNoCurveIsAttached() {
            Milestone m = milestone(MilestoneProgressModel.CURVE, 100, "GTE");

            assertThat(calculator.normalize(m, 50.0)).isEqualTo(0.5);
            verify(apCalculationService, never()).interpolate(any(UUID.class), anyDouble());
        }
    }

    @Nested
    @DisplayName("log")
    class Log {

        @Test
        void rankProgressUsesLogDistanceToTheTarget() {
            withPopulation(130000);
            Milestone m = milestone(MilestoneProgressModel.LOG, 50, "LTE");

            assertThat(calculator.normalize(m, 1000.0)).isCloseTo(0.619, within(TOLERANCE));
            assertThat(calculator.normalize(m, 5000.0)).isCloseTo(0.414, within(TOLERANCE));
            assertThat(calculator.normalize(m, 100.0)).isCloseTo(0.912, within(TOLERANCE));
        }

        @Test
        void percentileMilestonesUseAnExplicitFloorOfOne() {
            Milestone m = milestone(MilestoneProgressModel.LOG, 0.05, "LTE");
            m.setProgressFloor(1.0);

            assertThat(calculator.normalize(m, 0.058)).isCloseTo(0.950, within(TOLERANCE));
            assertThat(calculator.normalize(m, 0.5)).isCloseTo(0.231, within(TOLERANCE));
        }

        @Test
        void readsZeroAtTheBottomOfThePlayerbase() {
            withPopulation(130000);
            Milestone m = milestone(MilestoneProgressModel.LOG, 50, "LTE");

            assertThat(calculator.normalize(m, 130000.0)).isEqualTo(0.0);
        }

        @Test
        void returnsNullWhenPopulationCannotBeResolved() {
            when(categoryRepository.findByCodeAndActiveTrue("overall")).thenReturn(Optional.empty());
            Milestone m = milestone(MilestoneProgressModel.LOG, 50, "LTE");

            assertThat(calculator.normalize(m, 1000.0)).isNull();
        }

        @Test
        void higherIsBetterUsesLogDistanceFromTheFloor() {
            Milestone m = milestone(MilestoneProgressModel.LOG, 30000, "GTE");
            m.setProgressFloor(1000.0);

            assertThat(calculator.normalize(m, 20000.0)).isCloseTo(0.881, within(TOLERANCE));
            assertThat(calculator.normalize(m, 1000.0)).isEqualTo(0.0);
            assertThat(calculator.normalize(m, 30000.0)).isEqualTo(1.0);
        }

        @Test
        void higherIsBetterWithoutAFloorNeverReadsComplete() {
            withPopulation(130000);
            Milestone m = milestone(MilestoneProgressModel.LOG, 30000, "GTE");

            assertThat(calculator.normalize(m, 20000.0)).isNull();
            verify(statisticsRepository, never()).countActivePlayersInCategory(any(UUID.class));
        }

        @Test
        void resolvesPopulationOncePerCategory() {
            withPopulation(130000);
            Milestone m = milestone(MilestoneProgressModel.LOG, 50, "LTE");

            calculator.normalize(m, 1000.0);
            calculator.normalize(m, 900.0);
            calculator.normalize(m, 800.0);

            verify(statisticsRepository).countActivePlayersInCategory(OVERALL_ID);
        }
    }

    @Nested
    @DisplayName("gated milestones (a HAVING clause, e.g. Completionist X)")
    class Gated {

        @Test
        @DisplayName("takes the weakest link once both sides are known")
        void weakestLinkWins() {
            Milestone m = milestone(MilestoneProgressModel.LINEAR, 100, "GTE");

            assertThat(calculator.normalize(m, 100.0, 0.5)).isEqualTo(0.5);
            assertThat(calculator.normalize(m, 50.0, 1.0)).isEqualTo(0.5);
            assertThat(calculator.normalize(m, 100.0, 1.0)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("stays null when the gate is met but the value is missing")
        void nullWhenGateMetAndValueMissing() {
            Milestone m = milestone(MilestoneProgressModel.LINEAR, 100, "GTE");

            assertThat(calculator.normalize(m, null, 1.0)).isNull();
        }

        @Test
        @DisplayName("falls back to plain value progress when there is no gate")
        void ungatedIsUnchanged() {
            Milestone m = milestone(MilestoneProgressModel.LINEAR, 100, "GTE");

            assertThat(calculator.normalize(m, 25.0, null))
                    .isEqualTo(calculator.normalize(m, 25.0));
        }

        @Test
        @DisplayName("Completionist X: shows maps-played share, then switches to accuracy")
        void completionistXJourney() {
            Milestone m = milestone(MilestoneProgressModel.LINEAR, 0.96, "GTE");

            assertThat(calculator.normalize(m, null, 0.0)).isEqualTo(0.0);
            assertThat(calculator.normalize(m, null, 0.75)).isEqualTo(0.75);
            assertThat(calculator.normalize(m, 0.96, 1.0)).isEqualTo(1.0);
        }
    }
}
