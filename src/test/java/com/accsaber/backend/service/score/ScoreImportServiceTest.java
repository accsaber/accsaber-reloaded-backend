package com.accsaber.backend.service.score;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoresPage;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;

class ScoreImportServiceTest extends ScoreImportTestSupport {

        private ScoreImportService service;

        @BeforeEach
        void setUp() {
                service = new ScoreImportService(importer, recalculator, mapDifficultyRepository, mapComplexityService,
                                modifierCacheService, scoreRankingService, songSuggestService);
        }

        @Test
        void importsBeatLeaderScoresFirst() {
                when(beatLeaderClient.getLeaderboardScores("bl_123", 1, 100))
                                .thenReturn(List.of(buildBlScore("")));
                when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(STEAM_ID,
                                difficulty.getId()))
                                .thenReturn(Optional.empty());
                when(playerImportService.ensurePlayerExists(STEAM_ID))
                                .thenReturn(User.builder().id(STEAM_ID).name("Player").build());

                when(scoreSaberClient.getLeaderboardScores("ss_456", 1)).thenReturn(null);

                service.backfillDifficulty(difficulty);

                verify(scoreService).submitForBackfill(any(), any(), any());
                verify(playerImportService).ensurePlayerExists(STEAM_ID);
        }

        @Test
        void skipsExistingScores() {
                when(beatLeaderClient.getLeaderboardScores("bl_123", 1, 100))
                                .thenReturn(List.of(buildBlScore("")));
                Score existing = Score.builder().id(UUID.randomUUID()).scoreNoMods(900000).build();
                when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(STEAM_ID,
                                difficulty.getId()))
                                .thenReturn(Optional.of(existing));

                when(scoreSaberClient.getLeaderboardScores("ss_456", 1)).thenReturn(null);

                service.backfillDifficulty(difficulty);

                verify(scoreService, never()).submitForBackfill(any(), any(), any());
        }

        @Test
        void skipsScore_withBannedModifier() {
                when(beatLeaderClient.getLeaderboardScores("bl_123", 1, 100))
                                .thenReturn(List.of(buildBlScore("NO")));
                when(scoreSaberClient.getLeaderboardScores("ss_456", 1)).thenReturn(null);

                service.backfillDifficulty(difficulty);

                verify(scoreService, never()).submitForBackfill(any(), any(), any());
        }

        @Test
        void ssScoresSkipped_whenBlAlreadyImported() {
                when(beatLeaderClient.getLeaderboardScores("bl_123", 1, 100))
                                .thenReturn(List.of(buildBlScore("")));
                when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(STEAM_ID,
                                difficulty.getId()))
                                .thenReturn(Optional.empty())
                                .thenReturn(Optional.of(Score.builder().id(UUID.randomUUID())
                                                .scoreNoMods(890000).build()));
                when(playerImportService.ensurePlayerExists(STEAM_ID))
                                .thenReturn(User.builder().id(STEAM_ID).name("Player").build());

                ScoreSaberScoresPage ssPage = buildSsPage(buildSsScore(List.of()));
                when(scoreSaberClient.getLeaderboardScores("ss_456", 1)).thenReturn(ssPage);

                service.backfillDifficulty(difficulty);

                verify(scoreService, times(1)).submitForBackfill(any(), any(), any());
        }

        @Test
        void individualFailureDoesNotAbortBackfill() {
                BeatLeaderScoreResponse score1 = buildBlScore("");
                score1.setId(1L);
                BeatLeaderScoreResponse score2 = buildBlScore("");
                score2.setId(2L);
                score2.getPlayer().setId("76561198099999999");
                when(beatLeaderClient.getLeaderboardScores("bl_123", 1, 100))
                                .thenReturn(List.of(score1, score2));

                when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(anyLong(), any()))
                                .thenReturn(Optional.empty());

                when(playerImportService.ensurePlayerExists(STEAM_ID))
                                .thenThrow(new RuntimeException("API failure"));
                when(playerImportService.ensurePlayerExists(76561198099999999L))
                                .thenReturn(User.builder().id(76561198099999999L).name("P2").build());

                when(scoreSaberClient.getLeaderboardScores("ss_456", 1)).thenReturn(null);

                service.backfillDifficulty(difficulty);

                verify(scoreService, times(1)).submitForBackfill(any(), any(), any());
        }

        @Test
        void skipsBackfill_whenNoActiveComplexity() {
                when(mapComplexityService.findActiveComplexity(difficulty.getId()))
                                .thenReturn(Optional.empty());

                service.backfillDifficulty(difficulty);

                verify(beatLeaderClient, never()).getLeaderboardScores(any(), anyInt(), anyInt());
                verify(scoreService, never()).submitForBackfill(any(), any(), any());
        }
}
