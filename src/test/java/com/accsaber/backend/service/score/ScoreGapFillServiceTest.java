package com.accsaber.backend.service.score;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderScoreResponse;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;
import com.accsaber.backend.model.entity.map.LeaderboardPlatform;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;

class ScoreGapFillServiceTest extends ScoreImportTestSupport {

        private static final Instant SINCE = Instant.ofEpochSecond(1_000_000);

        private ScoreGapFillService service;

        @BeforeEach
        void setUp() {
                service = new ScoreGapFillService(importer, recalculator, mapDifficultyRepository,
                                mapComplexityService, modifierCacheService, scoreRankingService);
        }

        @Test
        void startupGapFillImportsUntilCutoffThenRecalculatesAndRepairsRanks() {
                BeatLeaderScoreResponse fresh = buildBlScore("");
                fresh.setTimepost(SINCE.getEpochSecond() + 10);
                BeatLeaderScoreResponse stale = buildBlScore("");
                stale.setTimepost(SINCE.getEpochSecond() - 10);
                when(mapDifficultyRepository.findByStatusAndActiveTrueWithCategory(MapDifficultyStatus.RANKED))
                                .thenReturn(List.of(difficulty));
                when(beatLeaderClient.getLeaderboardScoresSortedByDate("bl_123", 1, 100))
                                .thenReturn(List.of(fresh, stale));
                when(scoreSaberClient.getLeaderboardScoresSortedByDate("ss_456", 1)).thenReturn(null);
                when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(STEAM_ID, difficulty.getId()))
                                .thenReturn(Optional.empty());
                when(playerImportService.ensurePlayerExists(STEAM_ID))
                                .thenReturn(User.builder().id(STEAM_ID).name("Player").build());
                when(userCampaignRepository.findUserIdsByStatusesAndCampaignReleased(
                                UserCampaignStatus.PARTICIPATING, CampaignStatus.DRAFT))
                                .thenReturn(List.of(STEAM_ID));

                service.startupGapFillAllRankedDifficulties(SINCE);

                verify(scoreService).submitForBackfill(any(), any(), any());
                verify(beatLeaderClient, never()).getLeaderboardScoresSortedByDate("bl_123", 2, 100);
                verify(scoreRankingService).reassignRanks(difficulty.getId());
                verify(scoreRecalculationService).recalculatePlayers(Map.of(categoryId, Set.of(STEAM_ID)), Set.of(),
                                ScoreRecalculationService.PlayerRecalc.SCORE_IMPORT);
                verify(campaignEvaluationService).evaluateParticipatingForUser(STEAM_ID);
                verify(scoreRankingService).reassignAllRanks();
        }

        @Test
        void enrichOnlyGapFillMergesButNeverSubmits() {
                BeatLeaderScoreResponse bl = buildBlScore("");
                Score existing = Score.builder().id(UUID.randomUUID()).scoreNoMods(bl.getBaseScore()).build();
                when(beatLeaderClient.getRecentScores("bl_123", SINCE.getEpochSecond())).thenReturn(List.of(bl));
                when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(STEAM_ID, difficulty.getId()))
                                .thenReturn(Optional.of(existing));

                service.gapFillDifficulty(difficulty, SINCE, LeaderboardPlatform.BEATLEADER, true);

                verify(scoreRepository).save(existing);
                verify(scoreService, never()).submit(any());
                verify(scoreSaberClient, never()).getLeaderboardScoresSortedByDate(any(), anyInt());
        }
}
