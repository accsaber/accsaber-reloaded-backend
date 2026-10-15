package com.accsaber.backend.service.score;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderScoreResponse;
import com.accsaber.backend.model.entity.Modifier;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.score.ScoreModifierLink;
import com.accsaber.backend.model.entity.user.User;

class UserScoreBackfillServiceTest extends ScoreImportTestSupport {

        private UserScoreBackfillService service;

        @BeforeEach
        void setUp() {
                service = new UserScoreBackfillService(importer, recalculator, userRepository, duplicateUserService,
                                mapDifficultyRepository, mapComplexityService, modifierCacheService, scoreRepository);
        }

        @BeforeEach
        void userSetup() {
                lenient().when(userRepository.findByIdAndActiveTrue(STEAM_ID))
                                .thenReturn(Optional.of(User.builder().id(STEAM_ID).name("Player").build()));
                lenient().when(mapDifficultyRepository
                                .findByStatusAndActiveTrueWithCategory(MapDifficultyStatus.RANKED))
                                .thenReturn(List.of(difficulty));
        }

        @Test
        void skipsWhenUserNotFound() {
                when(userRepository.findByIdAndActiveTrue(STEAM_ID)).thenReturn(Optional.empty());

                service.backfillUser(STEAM_ID);

                verify(beatLeaderClient, never()).getPlayerScoreOnLeaderboard(any(), any(), any(), any());
                verify(scoreService, never()).submitForBackfill(any(), any(), any());
        }

        @Test
        void skipsWhenBlReturnsNoScore() {
                when(beatLeaderClient.getPlayerScoreOnLeaderboard(String.valueOf(STEAM_ID), "hash_123", "ExpertPlus", "Standard"))
                                .thenReturn(Optional.empty());

                service.backfillUser(STEAM_ID);

                verify(scoreService, never()).submitForBackfill(any(), any(), any());
                verify(scoreRankingService, never()).reassignRanks(any());
        }

        @Test
        void importsScoreWhenNoneExists() {
                when(beatLeaderClient.getPlayerScoreOnLeaderboard(String.valueOf(STEAM_ID), "hash_123", "ExpertPlus", "Standard"))
                                .thenReturn(Optional.of(buildBlScore("")));
                when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(STEAM_ID,
                                difficulty.getId()))
                                .thenReturn(Optional.empty());
                when(playerImportService.ensurePlayerExists(STEAM_ID))
                                .thenReturn(User.builder().id(STEAM_ID).name("Player").build());

                service.backfillUser(STEAM_ID);

                verify(scoreService, times(1)).submitForBackfill(any(), any(), any());
                verify(scoreRankingService, times(1)).reassignRanks(difficulty.getId());
                verify(scoreRecalculationService, times(1)).recalculatePlayers(
                                Map.of(categoryId, Set.of(STEAM_ID)), Set.of(),
                                ScoreRecalculationService.PlayerRecalc.SCORE_IMPORT);
                verify(campaignEvaluationService, times(1)).evaluateParticipatingForUser(STEAM_ID);
        }

        @Test
        void skipsWhenOurScoreIsHigher() {
                when(beatLeaderClient.getPlayerScoreOnLeaderboard(String.valueOf(STEAM_ID), "hash_123", "ExpertPlus", "Standard"))
                                .thenReturn(Optional.of(buildBlScore("")));
                Score existing = Score.builder()
                                .id(UUID.randomUUID())
                                .scoreNoMods(950000)
                                .build();
                when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(STEAM_ID,
                                difficulty.getId()))
                                .thenReturn(Optional.of(existing));

                service.backfillUser(STEAM_ID);

                verify(scoreService, never()).submitForBackfill(any(), any(), any());
                verify(scoreRankingService, never()).reassignRanks(any());
        }

        @Test
        void importsWhenIncomingScoreIsHigher() {
                when(beatLeaderClient.getPlayerScoreOnLeaderboard(String.valueOf(STEAM_ID), "hash_123", "ExpertPlus", "Standard"))
                                .thenReturn(Optional.of(buildBlScore("")));
                Score existing = Score.builder()
                                .id(UUID.randomUUID())
                                .scoreNoMods(800000)
                                .build();
                when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(STEAM_ID,
                                difficulty.getId()))
                                .thenReturn(Optional.of(existing));
                when(playerImportService.ensurePlayerExists(STEAM_ID))
                                .thenReturn(User.builder().id(STEAM_ID).name("Player").build());

                service.backfillUser(STEAM_ID);

                verify(scoreService, times(1)).submitForBackfill(any(), any(), any());
                verify(scoreRankingService, times(1)).reassignRanks(difficulty.getId());
        }

        @Test
        void enrichesWhenScoresAreEqual_andNoBlData() {
                BeatLeaderScoreResponse bl = buildBlScore("");
                when(beatLeaderClient.getPlayerScoreOnLeaderboard(String.valueOf(STEAM_ID), "hash_123", "ExpertPlus", "Standard"))
                                .thenReturn(Optional.of(bl));
                Score existing = Score.builder()
                                .id(UUID.randomUUID())
                                .scoreNoMods(bl.getBaseScore())
                                .build();
                when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(STEAM_ID,
                                difficulty.getId()))
                                .thenReturn(Optional.of(existing));

                service.backfillUser(STEAM_ID);

                verify(scoreRepository, times(1)).save(existing);
                verify(scoreService, never()).submitForBackfill(any(), any(), any());
        }

        @Test
        void reconcilesMissingModifierLink_onEqualScoreAlreadyBlBacked() {
                UUID ifId = UUID.randomUUID();
                when(modifierCacheService.getModifierCodeToId()).thenReturn(Map.of("IF", ifId));
                BeatLeaderScoreResponse bl = buildBlScore("IF");
                when(beatLeaderClient.getPlayerScoreOnLeaderboard(String.valueOf(STEAM_ID), "hash_123", "ExpertPlus", "Standard"))
                                .thenReturn(Optional.of(bl));

                Score existing = Score.builder()
                                .id(UUID.randomUUID())
                                .scoreNoMods(bl.getBaseScore())
                                .blScoreId(999L)
                                .build();
                when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(STEAM_ID,
                                difficulty.getId()))
                                .thenReturn(Optional.of(existing));
                when(scoreModifierLinkRepository.findByScore_Id(existing.getId())).thenReturn(List.of());
                Modifier ifMod = Modifier.builder().id(ifId).code("IF").build();
                when(modifierRepository.getReferenceById(ifId)).thenReturn(ifMod);

                service.backfillUser(STEAM_ID);

                verify(scoreModifierLinkRepository, times(1)).saveAll(anyIterable());
                verify(scoreService, never()).submitForBackfill(any(), any(), any());
                verify(scoreRepository, never()).save(any(Score.class));
        }

        @Test
        void doesNotAddModifierLink_whenAlreadyPresent() {
                UUID ifId = UUID.randomUUID();
                when(modifierCacheService.getModifierCodeToId()).thenReturn(Map.of("IF", ifId));
                BeatLeaderScoreResponse bl = buildBlScore("IF");
                when(beatLeaderClient.getPlayerScoreOnLeaderboard(String.valueOf(STEAM_ID), "hash_123", "ExpertPlus", "Standard"))
                                .thenReturn(Optional.of(bl));

                Score existing = Score.builder()
                                .id(UUID.randomUUID())
                                .scoreNoMods(bl.getBaseScore())
                                .blScoreId(999L)
                                .build();
                when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(STEAM_ID,
                                difficulty.getId()))
                                .thenReturn(Optional.of(existing));
                Modifier ifMod = Modifier.builder().id(ifId).code("IF").build();
                ScoreModifierLink link = ScoreModifierLink.builder()
                                .score(existing).modifier(ifMod).build();
                when(scoreModifierLinkRepository.findByScore_Id(existing.getId()))
                                .thenReturn(List.of(link));

                service.backfillUser(STEAM_ID);

                verify(scoreModifierLinkRepository, never()).saveAll(anyIterable());
        }

        @Test
        void skipsBannedModifier() {
                when(beatLeaderClient.getPlayerScoreOnLeaderboard(String.valueOf(STEAM_ID), "hash_123", "ExpertPlus", "Standard"))
                                .thenReturn(Optional.of(buildBlScore("NO")));

                service.backfillUser(STEAM_ID);

                verify(scoreService, never()).submitForBackfill(any(), any(), any());
                verify(scoreRankingService, never()).reassignRanks(any());
        }
}
