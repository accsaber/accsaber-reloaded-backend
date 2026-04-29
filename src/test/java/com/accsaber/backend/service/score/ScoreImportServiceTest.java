package com.accsaber.backend.service.score;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.accsaber.backend.client.BeatLeaderClient;
import com.accsaber.backend.client.ScoreSaberClient;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoresPage;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.Modifier;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.score.ScoreModifierLink;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.infra.ModifierCacheService;
import com.accsaber.backend.service.map.MapDifficultyComplexityService;
import com.accsaber.backend.service.map.MapDifficultyStatisticsService;
import com.accsaber.backend.service.milestone.MilestoneEvaluationService;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.player.PlayerImportService;
import com.accsaber.backend.service.stats.OverallStatisticsService;
import com.accsaber.backend.service.stats.RankingService;
import com.accsaber.backend.service.stats.StatisticsService;

@ExtendWith(MockitoExtension.class)
class ScoreImportServiceTest {

        private static final Long STEAM_ID = 76561198012345678L;
        private static final UUID NF_ID = UUID.randomUUID();
        private static final BigDecimal COMPLEXITY = BigDecimal.valueOf(8.0);

        @Mock
        private BeatLeaderClient beatLeaderClient;
        @Mock
        private ScoreSaberClient scoreSaberClient;
        @Mock
        private ScoreService scoreService;
        @Mock
        private PlayerImportService playerImportService;
        @Mock
        private MapDifficultyRepository mapDifficultyRepository;
        @Mock
        private MapDifficultyComplexityService mapComplexityService;
        @Mock
        private ModifierCacheService modifierCacheService;
        @Mock
        private ScoreRepository scoreRepository;
        @Mock
        private com.accsaber.backend.repository.score.ScoreModifierLinkRepository scoreModifierLinkRepository;
        @Mock
        private com.accsaber.backend.repository.ModifierRepository modifierRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private StatisticsService statisticsService;
        @Mock
        private OverallStatisticsService overallStatisticsService;
        @Mock
        private RankingService rankingService;
        @Mock
        private MilestoneEvaluationService milestoneEvaluationService;
        @Mock
        private MapDifficultyStatisticsService mapDifficultyStatisticsService;
        @Mock
        private ScoreRankingService scoreRankingService;
        @Mock
        private DuplicateUserService duplicateUserService;
        @Mock
        private com.accsaber.backend.service.skill.SkillService skillService;
        @Mock
        private com.accsaber.backend.service.songsuggest.SongSuggestService songSuggestService;

        private ScoreImportService scoreImportService;
        private MapDifficulty difficulty;
        private UUID categoryId;

        @BeforeEach
        void setUp() {
                scoreImportService = new ScoreImportService(
                                beatLeaderClient, scoreSaberClient, scoreService, playerImportService,
                                mapDifficultyRepository, mapComplexityService, scoreRepository,
                                scoreModifierLinkRepository, modifierRepository, userRepository,
                                modifierCacheService, statisticsService, overallStatisticsService, rankingService,
                                milestoneEvaluationService, mapDifficultyStatisticsService, scoreRankingService,
                                duplicateUserService, skillService, songSuggestService);

                ReflectionTestUtils.setField(scoreImportService, "backfillExecutor",
                                (java.util.concurrent.Executor) Runnable::run);

                lenient().when(modifierCacheService.getModifierCodeToId()).thenReturn(Map.of("NF", NF_ID));
                lenient().when(mapComplexityService.findActiveComplexity(any())).thenReturn(Optional.of(COMPLEXITY));
                lenient().when(duplicateUserService.resolvePrimaryUserId(anyLong()))
                                .thenAnswer(inv -> inv.getArgument(0));

                categoryId = UUID.randomUUID();
                Category category = Category.builder().id(categoryId).build();

                difficulty = MapDifficulty.builder()
                                .id(UUID.randomUUID())
                                .difficulty(Difficulty.EXPERT_PLUS)
                                .characteristic("Standard")
                                .status(MapDifficultyStatus.RANKED)
                                .maxScore(1_000_000)
                                .blLeaderboardId("bl_123")
                                .ssLeaderboardId("ss_456")
                                .category(category)
                                .active(true)
                                .build();

                lenient().when(milestoneEvaluationService.evaluateAllForUser(anyLong()))
                                .thenReturn(new MilestoneEvaluationService.EvaluationResult(List.of(), List.of()));
        }

        @Nested
        class BackfillUser {

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

                        scoreImportService.backfillUser(STEAM_ID);

                        verify(beatLeaderClient, never()).getPlayerScoreOnLeaderboard(any(), any());
                        verify(scoreService, never()).submitForBackfill(any(), any(), any());
                }

                @Test
                void skipsWhenBlReturnsNoScore() {
                        when(beatLeaderClient.getPlayerScoreOnLeaderboard(String.valueOf(STEAM_ID), "bl_123"))
                                        .thenReturn(Optional.empty());

                        scoreImportService.backfillUser(STEAM_ID);

                        verify(scoreService, never()).submitForBackfill(any(), any(), any());
                        verify(scoreRankingService, never()).reassignRanks(any());
                }

                @Test
                void importsScoreWhenNoneExists() {
                        when(beatLeaderClient.getPlayerScoreOnLeaderboard(String.valueOf(STEAM_ID), "bl_123"))
                                        .thenReturn(Optional.of(buildBlScore("")));
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(STEAM_ID,
                                        difficulty.getId()))
                                        .thenReturn(Optional.empty());
                        when(playerImportService.ensurePlayerExists(STEAM_ID))
                                        .thenReturn(User.builder().id(STEAM_ID).name("Player").build());

                        scoreImportService.backfillUser(STEAM_ID);

                        verify(scoreService, times(1)).submitForBackfill(any(), any(), any());
                        verify(scoreRankingService, times(1)).reassignRanks(difficulty.getId());
                        verify(statisticsService, times(1)).recalculate(STEAM_ID, categoryId, false);
                        verify(rankingService, times(1)).updateRankings(categoryId);
                }

                @Test
                void skipsWhenOurScoreIsHigher() {
                        when(beatLeaderClient.getPlayerScoreOnLeaderboard(String.valueOf(STEAM_ID), "bl_123"))
                                        .thenReturn(Optional.of(buildBlScore("")));
                        Score existing = Score.builder()
                                        .id(UUID.randomUUID())
                                        .scoreNoMods(950000)
                                        .build();
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(STEAM_ID,
                                        difficulty.getId()))
                                        .thenReturn(Optional.of(existing));

                        scoreImportService.backfillUser(STEAM_ID);

                        verify(scoreService, never()).submitForBackfill(any(), any(), any());
                        verify(scoreRankingService, never()).reassignRanks(any());
                }

                @Test
                void importsWhenIncomingScoreIsHigher() {
                        when(beatLeaderClient.getPlayerScoreOnLeaderboard(String.valueOf(STEAM_ID), "bl_123"))
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

                        scoreImportService.backfillUser(STEAM_ID);

                        verify(scoreService, times(1)).submitForBackfill(any(), any(), any());
                        verify(scoreRankingService, times(1)).reassignRanks(difficulty.getId());
                }

                @Test
                void enrichesWhenScoresAreEqual_andNoBlData() {
                        BeatLeaderScoreResponse bl = buildBlScore("");
                        when(beatLeaderClient.getPlayerScoreOnLeaderboard(String.valueOf(STEAM_ID), "bl_123"))
                                        .thenReturn(Optional.of(bl));
                        Score existing = Score.builder()
                                        .id(UUID.randomUUID())
                                        .scoreNoMods(bl.getBaseScore())
                                        .build();
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(STEAM_ID,
                                        difficulty.getId()))
                                        .thenReturn(Optional.of(existing));

                        scoreImportService.backfillUser(STEAM_ID);

                        verify(scoreRepository, times(1)).save(existing);
                        verify(scoreService, never()).submitForBackfill(any(), any(), any());
                }

                @Test
                void reconcilesMissingModifierLink_onEqualScoreAlreadyBlBacked() {
                        UUID ifId = UUID.randomUUID();
                        when(modifierCacheService.getModifierCodeToId()).thenReturn(Map.of("IF", ifId));
                        BeatLeaderScoreResponse bl = buildBlScore("IF");
                        when(beatLeaderClient.getPlayerScoreOnLeaderboard(String.valueOf(STEAM_ID), "bl_123"))
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
                        when(modifierRepository.findById(ifId)).thenReturn(Optional.of(ifMod));

                        scoreImportService.backfillUser(STEAM_ID);

                        verify(scoreModifierLinkRepository, times(1)).saveAll(anyIterable());
                        verify(scoreService, never()).submitForBackfill(any(), any(), any());
                        verify(scoreRepository, never()).save(any(Score.class));
                }

                @Test
                void doesNotAddModifierLink_whenAlreadyPresent() {
                        UUID ifId = UUID.randomUUID();
                        when(modifierCacheService.getModifierCodeToId()).thenReturn(Map.of("IF", ifId));
                        BeatLeaderScoreResponse bl = buildBlScore("IF");
                        when(beatLeaderClient.getPlayerScoreOnLeaderboard(String.valueOf(STEAM_ID), "bl_123"))
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

                        scoreImportService.backfillUser(STEAM_ID);

                        verify(scoreModifierLinkRepository, never()).saveAll(anyIterable());
                }

                @Test
                void skipsBannedModifier() {
                        when(beatLeaderClient.getPlayerScoreOnLeaderboard(String.valueOf(STEAM_ID), "bl_123"))
                                        .thenReturn(Optional.of(buildBlScore("NO")));

                        scoreImportService.backfillUser(STEAM_ID);

                        verify(scoreService, never()).submitForBackfill(any(), any(), any());
                        verify(scoreRankingService, never()).reassignRanks(any());
                }
        }

        @Nested
        class BackfillDifficulty {

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

                        scoreImportService.backfillDifficulty(difficulty);

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

                        scoreImportService.backfillDifficulty(difficulty);

                        verify(scoreService, never()).submitForBackfill(any(), any(), any());
                }

                @Test
                void skipsScore_withBannedModifier() {
                        when(beatLeaderClient.getLeaderboardScores("bl_123", 1, 100))
                                        .thenReturn(List.of(buildBlScore("NO")));
                        when(scoreSaberClient.getLeaderboardScores("ss_456", 1)).thenReturn(null);

                        scoreImportService.backfillDifficulty(difficulty);

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

                        ScoreSaberScoresPage ssPage = buildSsPage(buildSsScore(""));
                        when(scoreSaberClient.getLeaderboardScores("ss_456", 1)).thenReturn(ssPage);

                        scoreImportService.backfillDifficulty(difficulty);

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

                        scoreImportService.backfillDifficulty(difficulty);

                        verify(scoreService, times(1)).submitForBackfill(any(), any(), any());
                }

                @Test
                void skipsBackfill_whenNoActiveComplexity() {
                        when(mapComplexityService.findActiveComplexity(difficulty.getId()))
                                        .thenReturn(Optional.empty());

                        scoreImportService.backfillDifficulty(difficulty);

                        verify(beatLeaderClient, never()).getLeaderboardScores(any(), anyInt(), anyInt());
                        verify(scoreService, never()).submitForBackfill(any(), any(), any());
                }
        }

        private BeatLeaderScoreResponse buildBlScore(String modifiers) {
                BeatLeaderScoreResponse bl = new BeatLeaderScoreResponse();
                bl.setId(123456L);
                bl.setModifiedScore(950000);
                bl.setBaseScore(900000);
                bl.setRank(5);
                bl.setMaxCombo(500);
                bl.setBadCuts(3);
                bl.setMissedNotes(2);
                bl.setWallsHit(1);
                bl.setBombCuts(0);
                bl.setPauses(1);
                bl.setMaxStreak(200);
                bl.setPlayCount(10);
                bl.setHmd(64);
                bl.setModifiers(modifiers);
                bl.setLeaderboardId("bl_123");
                BeatLeaderScoreResponse.Player player = new BeatLeaderScoreResponse.Player();
                player.setId(String.valueOf(STEAM_ID));
                bl.setPlayer(player);
                return bl;
        }

        private ScoreSaberScoreResponse buildSsScore(String modifiers) {
                ScoreSaberScoreResponse ss = new ScoreSaberScoreResponse();
                ss.setId(789012L);
                ss.setModifiedScore(940000);
                ss.setBaseScore(890000);
                ss.setRank(7);
                ss.setMaxCombo(480);
                ss.setBadCuts(4);
                ss.setMissedNotes(3);
                ss.setDeviceHmd("Valve Index");
                ss.setModifiers(modifiers);
                ScoreSaberScoreResponse.LeaderboardPlayerInfo info = new ScoreSaberScoreResponse.LeaderboardPlayerInfo();
                info.setId(String.valueOf(STEAM_ID));
                ss.setLeaderboardPlayerInfo(info);
                return ss;
        }

        private ScoreSaberScoresPage buildSsPage(ScoreSaberScoreResponse... scores) {
                ScoreSaberScoresPage page = new ScoreSaberScoresPage();
                page.setScores(List.of(scores));
                ScoreSaberScoresPage.Metadata meta = new ScoreSaberScoresPage.Metadata();
                meta.setTotal(scores.length);
                meta.setPage(1);
                meta.setItemsPerPage(12);
                page.setMetadata(meta);
                return page;
        }

}
