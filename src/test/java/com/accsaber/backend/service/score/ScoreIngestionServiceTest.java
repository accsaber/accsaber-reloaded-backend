package com.accsaber.backend.service.score;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.config.PlatformProperties;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoreResponse;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.service.infra.MetricsService;
import com.accsaber.backend.service.infra.ModifierCacheService;
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
        private ModifierCacheService modifierCacheService;
        @Mock
        private ScoreRepository scoreRepository;
        @Mock
        private ScoreImportService scoreImportService;

        private MetricsService metricsService;
        private ScheduledExecutorService scheduler;
        private ScoreIngestionService ingestionService;
        private MapDifficulty difficulty;

        @BeforeEach
        void setUp() {
                PlatformProperties properties = new PlatformProperties();
                properties.setSsWaitForBlSeconds(1);
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

                ingestionService = new ScoreIngestionService(
                                scoreService, playerImportService, mapDifficultyRepository,
                                scoreRepository, scoreImportService, modifierCacheService,
                                properties, scheduler, metricsService);
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

                        verify(scoreService).submit(any());
                }

                @Test
                void skipsScore_forUnrankedMap() {
                        BeatLeaderScoreResponse blScore = buildBlScore("unranked_999", "");

                        ingestionService.handleBeatLeaderScore(blScore);

                        verify(scoreService, never()).submit(any());
                        verifyNoInteractions(playerImportService);
                }

                @Test
                void skipsScore_withBannedModifier() {
                        BeatLeaderScoreResponse blScore = buildBlScore("bl_123", "SF");
                        when(mapDifficultyRepository.findByBlLeaderboardId("bl_123"))
                                        .thenReturn(Optional.of(difficulty));

                        ingestionService.handleBeatLeaderScore(blScore);

                        verify(scoreService, never()).submit(any());
                }

                @Test
                void skipsScore_whenPlayerDataMissing() {
                        BeatLeaderScoreResponse blScore = buildBlScore("bl_123", "");
                        blScore.setPlayer(null);
                        when(mapDifficultyRepository.findByBlLeaderboardId("bl_123"))
                                        .thenReturn(Optional.of(difficulty));

                        ingestionService.handleBeatLeaderScore(blScore);

                        verify(scoreService, never()).submit(any());
                }
        }

        @Nested
        class HandleScoreSaberScore {

                @Test
                void submitsScore_afterDelay_whenNoBeatLeaderScoreArrives() throws Exception {
                        ScoreSaberScoreResponse ssScore = buildSsScore("");
                        when(mapDifficultyRepository.findBySsLeaderboardId("ss_456"))
                                        .thenReturn(Optional.of(difficulty));
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(STEAM_ID,
                                        difficulty.getId()))
                                        .thenReturn(Optional.empty());
                        when(playerImportService.ensurePlayerExists(STEAM_ID))
                                        .thenReturn(User.builder().id(STEAM_ID).name("Player").build());
                        when(scoreService.submit(any())).thenReturn(buildScoreResponse());

                        ingestionService.handleScoreSaberScore(ssScore, STEAM_ID, "ss_456");

                        verify(scoreService, never()).submit(any());

                        scheduler.awaitTermination(2, TimeUnit.SECONDS);
                        Thread.sleep(200);

                        verify(scoreService).submit(any());
                }

                @Test
                void cancelsSsScore_whenBlScoreArrivesFirst() throws Exception {
                        ScoreSaberScoreResponse ssScore = buildSsScore("");
                        when(mapDifficultyRepository.findBySsLeaderboardId("ss_456"))
                                        .thenReturn(Optional.of(difficulty));
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(STEAM_ID,
                                        difficulty.getId()))
                                        .thenReturn(Optional.empty());

                        ingestionService.handleScoreSaberScore(ssScore, STEAM_ID, "ss_456");

                        BeatLeaderScoreResponse blScore = buildBlScore("bl_123", "");
                        when(mapDifficultyRepository.findByBlLeaderboardId("bl_123"))
                                        .thenReturn(Optional.of(difficulty));
                        when(playerImportService.ensurePlayerExists(STEAM_ID))
                                        .thenReturn(User.builder().id(STEAM_ID).name("Player").build());
                        when(scoreService.submit(any())).thenReturn(buildScoreResponse());

                        ingestionService.handleBeatLeaderScore(blScore);

                        Thread.sleep(2000);

                        verify(scoreService, times(1)).submit(any());
                }

                @Test
                void skipsScore_forUnrankedMap() {
                        ScoreSaberScoreResponse ssScore = buildSsScore("");

                        ingestionService.handleScoreSaberScore(ssScore, STEAM_ID, "unranked_999");

                        verify(scoreService, never()).submit(any());
                }

                @Test
                void skipsScore_withBannedModifier() {
                        ScoreSaberScoreResponse ssScore = buildSsScore("NO");

                        ingestionService.handleScoreSaberScore(ssScore, STEAM_ID, "ss_456");

                        verify(scoreService, never()).submit(any());
                }
        }

        private BeatLeaderScoreResponse buildBlScore(String leaderboardId, String modifiers) {
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
                bl.setModifiers(modifiers);
                bl.setLeaderboardId(leaderboardId);
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
                ss.setModifiers(modifiers);
                ScoreSaberScoreResponse.LeaderboardPlayerInfo info = new ScoreSaberScoreResponse.LeaderboardPlayerInfo();
                info.setId(String.valueOf(STEAM_ID));
                ss.setLeaderboardPlayerInfo(info);
                return ss;
        }

        private ScoreResponse buildScoreResponse() {
                return ScoreResponse.builder()
                                .id(UUID.randomUUID())
                                .userId(STEAM_ID)
                                .mapDifficultyId(difficulty.getId())
                                .score(950000)
                                .ap(new BigDecimal("100.0"))
                                .build();
        }
}
