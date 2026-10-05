package com.accsaber.backend.service.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.repository.score.ScoreRepository;

@ExtendWith(MockitoExtension.class)
class ScoreRankingServiceTest {

    @Mock
    private ScoreRepository scoreRepository;

    @InjectMocks
    private ScoreRankingService scoreRankingService;

    private static final UUID DIFF_ID = UUID.randomUUID();
    private static final Instant TIME_SET = Instant.parse("2025-06-15T12:00:00Z");

    @Nested
    class RankNewScore {

        @ParameterizedTest(name = "{1} scores above ranks the new score {2}")
        @CsvSource({
                "500.0, 0, 1",
                "300.0, 5, 6"
        })
        void ranksBelowEveryScoreAbove(double ap, int scoresAbove, int expectedRank) {
            when(scoreRepository.countActiveScoresRankedAbove(DIFF_ID, ap, TIME_SET)).thenReturn(scoresAbove);

            int rank = scoreRankingService.rankNewScore(DIFF_ID, ap, TIME_SET);

            assertThat(rank).isEqualTo(expectedRank);
            verify(scoreRepository).shiftScoreRanksDown(DIFF_ID, expectedRank);
        }

        @Test
        void countsBeforeShifting() {
            Double ap = 400.000000;
            when(scoreRepository.countActiveScoresRankedAbove(DIFF_ID, ap, TIME_SET)).thenReturn(2);

            scoreRankingService.rankNewScore(DIFF_ID, ap, TIME_SET);

            InOrder order = inOrder(scoreRepository);
            order.verify(scoreRepository).countActiveScoresRankedAbove(DIFF_ID, ap, TIME_SET);
            order.verify(scoreRepository).shiftScoreRanksDown(DIFF_ID, 3);
        }
    }

    @Nested
    class RankImprovedScore {

        @ParameterizedTest(name = "from rank {1} with {2} above lands at {3}")
        @CsvSource({
                "600.0, 3, 1, 2",
                "999.0, 5, 0, 1",
                "400.0, 3, 2, 3"
        })
        void closesGapThenInsertsAtNewPosition(double newAp, int oldRank, int scoresAbove, int expectedRank) {
            when(scoreRepository.countActiveScoresRankedAbove(DIFF_ID, newAp, TIME_SET)).thenReturn(scoresAbove);

            int rank = scoreRankingService.rankImprovedScore(DIFF_ID, oldRank, newAp, TIME_SET);

            assertThat(rank).isEqualTo(expectedRank);
            InOrder order = inOrder(scoreRepository);
            order.verify(scoreRepository).shiftScoreRanksUp(DIFF_ID, oldRank);
            order.verify(scoreRepository).countActiveScoresRankedAbove(DIFF_ID, newAp, TIME_SET);
            order.verify(scoreRepository).shiftScoreRanksDown(DIFF_ID, expectedRank);
        }
    }
}
