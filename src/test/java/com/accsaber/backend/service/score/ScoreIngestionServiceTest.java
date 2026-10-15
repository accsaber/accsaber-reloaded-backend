package com.accsaber.backend.service.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.client.ScoreSaberClient;
import com.accsaber.backend.config.PlatformProperties;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoreStats;
import com.accsaber.backend.model.dto.request.score.SubmitScoreRequest;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.map.MapDifficultyLeaderboardAliasRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.service.infra.MetricsService;
import com.accsaber.backend.service.infra.ModifierCacheService;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.player.PlayerImportService;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = LENIENT)
class ScoreIngestionServiceTest {

        private static final Long STEAM_ID = 76561198012345678L;
        private static final UUID NF_ID = UUID.randomUUID();

        @Mock
        private ScoreService scoreService;
        @Mock
        private PlayerImportService playerImportService;
        @Mock
        private MapDifficultyRepository mapDifficultyRepository;
        @Mock
        private MapDifficultyLeaderboardAliasRepository aliasRepository;
        @Mock
        private ModifierCacheService modifierCacheService;
        @Mock
        private ScoreGapFillService scoreGapFillService;
        @Mock
        private DuplicateUserService duplicateUserService;
        @Mock
        private ScoreSaberClient scoreSaberClient;
        @Mock
        private CampaignScoreGate campaignScoreGate;

        private MetricsService metricsService;
        private ScheduledExecutorService scheduler;
        private ScoreIngestionService ingestionService;
        private MapDifficulty difficulty;

        @BeforeEach
        void setUp() {
                PlatformProperties properties = new PlatformProperties();
                properties.setGapFillWindowSeconds(60);

                scheduler = Executors.newScheduledThreadPool(1);

                org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor noopExecutor = new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
                noopExecutor.setCorePoolSize(1);
                noopExecutor.setMaxPoolSize(1);
                noopExecutor.setQueueCapacity(1);
                noopExecutor.initialize();
                metricsService = new MetricsService(
                                new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
                                noopExecutor, noopExecutor, noopExecutor);

                when(modifierCacheService.getModifierCodeToId()).thenReturn(Map.of("NF", NF_ID));
                when(duplicateUserService.resolvePrimaryUserId(STEAM_ID)).thenReturn(STEAM_ID);
                when(scoreSaberClient.getScoreStats(any())).thenReturn(Optional.empty());

                difficulty = MapDifficulty.builder()
                                .id(UUID.randomUUID())
                                .difficulty(Difficulty.EXPERT_PLUS)
                                .characteristic("Standard")
                                .status(MapDifficultyStatus.RANKED)
                                .maxScore(1_000_000)
                                .blLeaderboardId("bl_123")
                                .ssLeaderboardId("ss_456")
                                .active(true)
                                .build();

                when(mapDifficultyRepository.findByStatusAndActiveTrue(MapDifficultyStatus.RANKED))
                                .thenReturn(java.util.List.of(difficulty));
                when(aliasRepository.findRankedBlLeaderboardIds()).thenReturn(java.util.List.of());
                when(aliasRepository.findRankedSsLeaderboardIds()).thenReturn(java.util.List.of());

                ingestionService = new ScoreIngestionService(
                                scoreService, playerImportService, mapDifficultyRepository,
                                aliasRepository, scoreGapFillService, modifierCacheService,
                                properties, scheduler, metricsService, duplicateUserService,
                                scoreSaberClient, campaignScoreGate);
                ingestionService.init();
        }

        @AfterEach
        void tearDown() {
                scheduler.shutdownNow();
        }

        @Nested
        class HandleBeatLeaderScore {

                @Test
                void submitsScore_forRankedMap() {
                        BeatLeaderScoreResponse blScore = buildBlScore("bl_123", "");
                        when(mapDifficultyRepository.findByBlLeaderboardId("bl_123"))
                                        .thenReturn(Optional.of(difficulty));
                        when(playerImportService.ensurePlayerExists(STEAM_ID))
                                        .thenReturn(User.builder().id(STEAM_ID).name("Player").build());
                        when(scoreService.submit(any())).thenReturn(buildScoreResponse());

                        ingestionService.handleBeatLeaderScore(blScore);

                        verify(scoreService, timeout(2000)).submit(any());
                }

                @Test
                void skipsScore_forUnrankedMap() {
                        BeatLeaderScoreResponse blScore = buildBlScore("unranked_999", "");

                        ingestionService.handleBeatLeaderScore(blScore);

                        verify(scoreService, after(500).never()).submit(any());
                        verifyNoInteractions(playerImportService);
                }

                @Test
                void skipsScore_withBannedModifier_whenNotACampaignParticipant() {
                        BeatLeaderScoreResponse blScore = buildBlScore("bl_123", "NO");
                        when(mapDifficultyRepository.findByBlLeaderboardId("bl_123"))
                                        .thenReturn(Optional.of(difficulty));
                        when(campaignScoreGate.isParticipant(STEAM_ID)).thenReturn(false);

                        ingestionService.handleBeatLeaderScore(blScore);

                        verify(scoreService, after(500).never()).submit(any());
                        verify(scoreService, never()).submitCampaignScore(any());
                }

                @Test
                void bannedModifierScore_isRecordedAlongsideTheCleanSsScore() {
                        ScoreSaberScoreResponse ssScore = buildSsScore("");
                        when(mapDifficultyRepository.findBySsLeaderboardId("ss_456"))
                                        .thenReturn(Optional.of(difficulty));
                        when(playerImportService.ensurePlayerExists(STEAM_ID))
                                        .thenReturn(User.builder().id(STEAM_ID).name("Player").build());
                        when(scoreService.submit(any())).thenReturn(buildScoreResponse());

                        ingestionService.handleScoreSaberScore(ssScore, null, STEAM_ID, "ss_456");

                        BeatLeaderScoreResponse bannedBl = buildBlScore("bl_123", "NO");
                        when(mapDifficultyRepository.findByBlLeaderboardId("bl_123"))
                                        .thenReturn(Optional.of(difficulty));
                        when(campaignScoreGate.isParticipant(STEAM_ID)).thenReturn(true);

                        ingestionService.handleBeatLeaderScore(bannedBl);

                        verify(scoreService, timeout(2000)).submitCampaignScore(any());
                        verify(scoreService, timeout(2000)).submit(any());
                }

                @Test
                void recordsBannedModifierScore_asCampaignAttempt_forParticipantOnRankedMap() {
                        BeatLeaderScoreResponse blScore = buildBlScore("bl_123", "NO");
                        when(mapDifficultyRepository.findByBlLeaderboardId("bl_123"))
                                        .thenReturn(Optional.of(difficulty));
                        when(campaignScoreGate.isParticipant(STEAM_ID)).thenReturn(true);

                        ingestionService.handleBeatLeaderScore(blScore);

                        verify(scoreService, timeout(2000)).submitCampaignScore(any());
                        verify(scoreService, never()).submit(any());
                        verifyNoInteractions(playerImportService);
                }

                @Test
                void skipsScore_whenPlayerDataMissing() {
                        BeatLeaderScoreResponse blScore = buildBlScore("bl_123", "");
                        blScore.setPlayer(null);
                        when(mapDifficultyRepository.findByBlLeaderboardId("bl_123"))
                                        .thenReturn(Optional.of(difficulty));

                        ingestionService.handleBeatLeaderScore(blScore);

                        verify(scoreService, after(500).never()).submit(any());
                }
        }

        @Nested
        class HandleScoreSaberScore {

                @Test
                void submitsScore_withoutWaitingForBeatLeader() {
                        ScoreSaberScoreResponse ssScore = buildSsScore("");
                        when(mapDifficultyRepository.findBySsLeaderboardId("ss_456"))
                                        .thenReturn(Optional.of(difficulty));
                        when(playerImportService.ensurePlayerExists(STEAM_ID))
                                        .thenReturn(User.builder().id(STEAM_ID).name("Player").build());
                        when(scoreService.submit(any())).thenReturn(buildScoreResponse());

                        ingestionService.handleScoreSaberScore(ssScore, null, STEAM_ID, "ss_456");

                        verify(scoreService, timeout(2000)).submit(any());
                }

                @Test
                void submitsBothPlatforms_leavingThePlayMergeToScoreService() {
                        ScoreSaberScoreResponse ssScore = buildSsScore("");
                        when(mapDifficultyRepository.findBySsLeaderboardId("ss_456"))
                                        .thenReturn(Optional.of(difficulty));
                        when(mapDifficultyRepository.findByBlLeaderboardId("bl_123"))
                                        .thenReturn(Optional.of(difficulty));
                        when(playerImportService.ensurePlayerExists(STEAM_ID))
                                        .thenReturn(User.builder().id(STEAM_ID).name("Player").build());
                        when(scoreService.submit(any())).thenReturn(buildScoreResponse());

                        ingestionService.handleScoreSaberScore(ssScore, null, STEAM_ID, "ss_456");
                        ingestionService.handleBeatLeaderScore(buildBlScore("bl_123", ""));

                        ArgumentCaptor<SubmitScoreRequest> captor = ArgumentCaptor.forClass(SubmitScoreRequest.class);
                        verify(scoreService, timeout(2000).times(2)).submit(captor.capture());
                        assertThat(captor.getAllValues())
                                        .anySatisfy(r -> assertThat(r.getSsScoreId()).isEqualTo(789012L))
                                        .anySatisfy(r -> assertThat(r.getBlScoreId()).isEqualTo(123456L));
                }

                @Test
                void submitsBaseScore_notThePlatformModifiedScore() {
                        ScoreSaberScoreResponse ssScore = buildSsScore("NF");
                        ssScore.setUnmodifiedScore(900000);
                        when(mapDifficultyRepository.findBySsLeaderboardId("ss_456"))
                                        .thenReturn(Optional.of(difficulty));
                        when(playerImportService.ensurePlayerExists(STEAM_ID))
                                        .thenReturn(User.builder().id(STEAM_ID).name("Player").build());
                        when(scoreService.submit(any())).thenReturn(buildScoreResponse());

                        ingestionService.handleScoreSaberScore(ssScore, null, STEAM_ID, "ss_456");

                        ArgumentCaptor<SubmitScoreRequest> captor = ArgumentCaptor.forClass(SubmitScoreRequest.class);
                        verify(scoreService, timeout(2000)).submit(captor.capture());
                        assertThat(captor.getValue().getScore()).isEqualTo(900000);
                        assertThat(captor.getValue().getScoreNoMods()).isEqualTo(900000);
                }

                @Test
                void fetchesScoreStats_whenNull_andPopulatesFromStatsEndpoint() throws Exception {
                        ScoreSaberScoreResponse ssScore = buildSsScore("");
                        when(mapDifficultyRepository.findBySsLeaderboardId("ss_456"))
                                        .thenReturn(Optional.of(difficulty));
                        when(playerImportService.ensurePlayerExists(STEAM_ID))
                                        .thenReturn(User.builder().id(STEAM_ID).name("Player").build());
                        when(scoreService.submit(any())).thenReturn(buildScoreResponse());

                        ScoreSaberScoreStats stats = new ScoreSaberScoreStats();
                        stats.setMax115Streak(150);
                        stats.setLeftBombs(1);
                        stats.setRightBombs(2);
                        when(scoreSaberClient.getScoreStats(789012L)).thenReturn(Optional.of(stats));

                        ingestionService.handleScoreSaberScore(ssScore, null, STEAM_ID, "ss_456");

                        ArgumentCaptor<SubmitScoreRequest> captor = ArgumentCaptor.forClass(SubmitScoreRequest.class);
                        verify(scoreService, timeout(2000)).submit(captor.capture());
                        verify(scoreSaberClient).getScoreStats(789012L);
                        assertThat(captor.getValue().getStreak115()).isEqualTo(150);
                        assertThat(captor.getValue().getBombHits()).isEqualTo(3);
                        assertThat(captor.getValue().getSsScoreId()).isEqualTo(789012L);
                }

                @Test
                void skipsScore_forUnrankedMap() {
                        ScoreSaberScoreResponse ssScore = buildSsScore("");

                        ingestionService.handleScoreSaberScore(ssScore, null, STEAM_ID, "unranked_999");

                        verify(scoreService, never()).submit(any());
                }

                @Test
                void skipsScore_withBannedModifier_whenNotACampaignParticipant() {
                        ScoreSaberScoreResponse ssScore = buildSsScore("NO");
                        when(campaignScoreGate.isParticipant(STEAM_ID)).thenReturn(false);

                        ingestionService.handleScoreSaberScore(ssScore, null, STEAM_ID, "ss_456");

                        verify(scoreService, never()).submit(any());
                        verify(scoreService, never()).submitCampaignScore(any());
                }

                @Test
                void recordsBannedModifierScore_asCampaignAttempt_forParticipantOnRankedMap() {
                        ScoreSaberScoreResponse ssScore = buildSsScore("NO");
                        when(mapDifficultyRepository.findBySsLeaderboardId("ss_456"))
                                        .thenReturn(Optional.of(difficulty));
                        when(campaignScoreGate.isParticipant(STEAM_ID)).thenReturn(true);

                        ingestionService.handleScoreSaberScore(ssScore, null, STEAM_ID, "ss_456");

                        verify(scoreService, timeout(2000)).submitCampaignScore(any());
                        verify(scoreService, never()).submit(any());
                }
        }

        private BeatLeaderScoreResponse buildBlScore(String leaderboardId, String modifiers) {
                BeatLeaderScoreResponse bl = new BeatLeaderScoreResponse();
                bl.setId(123456L);
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
                bl.setModifiers(modifiers);
                bl.setLeaderboardId(leaderboardId);
                BeatLeaderScoreResponse.Player player = new BeatLeaderScoreResponse.Player();
                player.setId(String.valueOf(STEAM_ID));
                bl.setPlayer(player);
                return bl;
        }

        private ScoreSaberScoreResponse buildSsScore(String modifier) {
                ScoreSaberScoreResponse ss = new ScoreSaberScoreResponse();
                ss.setId(789012L);
                ss.setUnmodifiedScore(890000);
                ss.setRank(7);
                ss.setMaxCombo(480);
                ss.setBadCuts(4);
                ss.setMissedNotes(3);
                ss.setMods(modifier == null || modifier.isBlank() ? java.util.List.of() : java.util.List.of(modifier));
                ScoreSaberScoreResponse.Player player = new ScoreSaberScoreResponse.Player();
                player.setId(String.valueOf(STEAM_ID));
                ss.setPlayer(player);
                return ss;
        }

        private ScoreResponse buildScoreResponse() {
                return ScoreResponse.builder()
                                .id(UUID.randomUUID())
                                .userId(String.valueOf(STEAM_ID))
                                .mapDifficultyId(difficulty.getId())
                                .score(950000)
                                .ap(100.0)
                                .build();
        }
}
