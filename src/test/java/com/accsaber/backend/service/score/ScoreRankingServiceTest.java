package com.accsaber.backend.service.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Nested
    class ReassignRanks {

        @Test
        void delegatesToRepository() {
            scoreRankingService.reassignRanks(DIFF_ID);

            verify(scoreRepository).reassignScoreRanks(DIFF_ID);
        }
    }

    @Nested
    class RankNewScore {

        @Test
        void firstScoreOnDifficulty_getsRank1() {
            BigDecimal ap = new BigDecimal("500.000000");
            when(scoreRepository.countActiveScoresWithHigherAp(DIFF_ID, ap)).thenReturn(0);

            int rank = scoreRankingService.rankNewScore(DIFF_ID, ap);

            assertThat(rank).isEqualTo(1);
            verify(scoreRepository).shiftScoreRanksDown(DIFF_ID, 1);
        }

        @Test
        void scoresAboveExist_ranksBelow() {
            BigDecimal ap = new BigDecimal("300.000000");
            when(scoreRepository.countActiveScoresWithHigherAp(DIFF_ID, ap)).thenReturn(5);

            int rank = scoreRankingService.rankNewScore(DIFF_ID, ap);

            assertThat(rank).isEqualTo(6);
            verify(scoreRepository).shiftScoreRanksDown(DIFF_ID, 6);
        }

        @Test
        void countsBeforeShifting() {
            BigDecimal ap = new BigDecimal("400.000000");
            when(scoreRepository.countActiveScoresWithHigherAp(DIFF_ID, ap)).thenReturn(2);

            scoreRankingService.rankNewScore(DIFF_ID, ap);

            InOrder order = inOrder(scoreRepository);
            order.verify(scoreRepository).countActiveScoresWithHigherAp(DIFF_ID, ap);
            order.verify(scoreRepository).shiftScoreRanksDown(DIFF_ID, 3);
        }
    }

    @Nested
    class RankImprovedScore {

        @Test
        void closesGapThenInsertsAtNewPosition() {
            BigDecimal newAp = new BigDecimal("600.000000");
            int oldRank = 3;
            when(scoreRepository.countActiveScoresWithHigherAp(DIFF_ID, newAp)).thenReturn(1);

            int rank = scoreRankingService.rankImprovedScore(DIFF_ID, oldRank, newAp);

            assertThat(rank).isEqualTo(2);
            InOrder order = inOrder(scoreRepository);
            order.verify(scoreRepository).shiftScoreRanksUp(DIFF_ID, 3);
            order.verify(scoreRepository).countActiveScoresWithHigherAp(DIFF_ID, newAp);
            order.verify(scoreRepository).shiftScoreRanksDown(DIFF_ID, 2);
        }

        @Test
        void improvesToFirstPlace() {
            BigDecimal newAp = new BigDecimal("999.000000");
            int oldRank = 5;
            when(scoreRepository.countActiveScoresWithHigherAp(DIFF_ID, newAp)).thenReturn(0);

            int rank = scoreRankingService.rankImprovedScore(DIFF_ID, oldRank, newAp);

            assertThat(rank).isEqualTo(1);
            InOrder order = inOrder(scoreRepository);
            order.verify(scoreRepository).shiftScoreRanksUp(DIFF_ID, 5);
            order.verify(scoreRepository).shiftScoreRanksDown(DIFF_ID, 1);
        }

        @Test
        void staysAtSamePosition() {
            BigDecimal newAp = new BigDecimal("400.000000");
            int oldRank = 3;
            when(scoreRepository.countActiveScoresWithHigherAp(DIFF_ID, newAp)).thenReturn(2);

            int rank = scoreRankingService.rankImprovedScore(DIFF_ID, oldRank, newAp);

            assertThat(rank).isEqualTo(3);
        }
    }

    @Nested
    class ReassignAllRanks {

        @Test
        void noDifficulties_skips() {
            when(scoreRepository.findDistinctActiveDifficultyIds()).thenReturn(Collections.emptyList());

            scoreRankingService.reassignAllRanks();

            verify(scoreRepository).findDistinctActiveDifficultyIds();
        }

        @Test
        void reassignsEachDifficulty() {
            UUID diff1 = UUID.randomUUID();
            UUID diff2 = UUID.randomUUID();
            UUID diff3 = UUID.randomUUID();
            when(scoreRepository.findDistinctActiveDifficultyIds()).thenReturn(List.of(diff1, diff2, diff3));

            scoreRankingService.reassignAllRanks();

            verify(scoreRepository).reassignScoreRanks(diff1);
            verify(scoreRepository).reassignScoreRanks(diff2);
            verify(scoreRepository).reassignScoreRanks(diff3);
        }
    }
}
