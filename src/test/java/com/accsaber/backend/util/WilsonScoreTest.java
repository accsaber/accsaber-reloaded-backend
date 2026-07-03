package com.accsaber.backend.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WilsonScoreTest {

    private static final double TOLERANCE = 0.001;

    @Test
    void zeroVotesScoresZero() {
        assertThat(WilsonScore.lowerBound(0, 0)).isEqualTo(0.0);
    }

    @Test
    void higherVolumeOutranksSmallSampleAtSameRatio() {
        assertThat(WilsonScore.lowerBound(50, 50)).isCloseTo(0.929, org.assertj.core.data.Offset.offset(TOLERANCE));
        assertThat(WilsonScore.lowerBound(5, 5)).isCloseTo(0.566, org.assertj.core.data.Offset.offset(TOLERANCE));
        assertThat(WilsonScore.lowerBound(1, 1)).isCloseTo(0.207, org.assertj.core.data.Offset.offset(TOLERANCE));
        assertThat(WilsonScore.lowerBound(50, 50)).isGreaterThan(WilsonScore.lowerBound(5, 5));
        assertThat(WilsonScore.lowerBound(5, 5)).isGreaterThan(WilsonScore.lowerBound(1, 1));
    }

    @Test
    void nearlyUnanimousLargeSampleBeatsTinyPerfectSample() {
        assertThat(WilsonScore.lowerBound(999, 1000)).isCloseTo(0.994,
                org.assertj.core.data.Offset.offset(TOLERANCE));
        assertThat(WilsonScore.lowerBound(999, 1000)).isGreaterThan(WilsonScore.lowerBound(1, 1));
    }

    @Test
    void downvotesLowerTheScore() {
        assertThat(WilsonScore.lowerBound(80, 100)).isLessThan(WilsonScore.lowerBound(100, 100));
    }
}
