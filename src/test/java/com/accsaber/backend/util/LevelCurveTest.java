package com.accsaber.backend.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LevelCurveTest {

    private final LevelCurve curve = new LevelCurve(52.0, 1.2, 100);

    @Nested
    class XpForLevel {

        @Test
        void zeroAndNegativeLevelsCostNothing() {
            assertThat(curve.xpForLevel(0)).isZero();
            assertThat(curve.xpForLevel(-3)).isZero();
        }

        @Test
        void costIsFlooredPowerOfTheLevel() {
            assertThat(curve.xpForLevel(1)).isEqualTo(52.0);
            assertThat(curve.xpForLevel(2)).isEqualTo(119.0);
        }

        @Test
        void costStopsGrowingAtTheCap() {
            assertThat(curve.xpForLevel(250)).isEqualTo(curve.xpForLevel(100));
        }
    }

    @Nested
    class ProgressAt {

        @Test
        void noXpSitsAtLevelZeroFacingTheFirstCost() {
            LevelCurve.Progress progress = curve.progressAt(0.0);

            assertThat(progress.level()).isZero();
            assertThat(progress.xpIntoLevel()).isZero();
            assertThat(progress.xpForNextLevel()).isEqualTo(52.0);
        }

        @Test
        void landingExactlyOnAThresholdCountsAsReachingIt() {
            LevelCurve.Progress progress = curve.progressAt(52.0 + 119.0);

            assertThat(progress.level()).isEqualTo(2);
            assertThat(progress.xpIntoLevel()).isZero();
        }

        @Test
        void leftoverXpCarriesIntoTheNextLevel() {
            LevelCurve.Progress progress = curve.progressAt(60.0);

            assertThat(progress.level()).isEqualTo(1);
            assertThat(progress.xpIntoLevel()).isEqualTo(8.0);
            assertThat(progress.xpForNextLevel()).isEqualTo(119.0);
        }
    }
}
