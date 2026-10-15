package com.accsaber.backend.service.score;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.client.BeatLeaderClient;
import com.accsaber.backend.client.ScoreSaberClient;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoresPage;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.ModifierRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignRepository;
import com.accsaber.backend.repository.campaign.UserCampaignRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreModifierLinkRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.campaign.CampaignEvaluationService;
import com.accsaber.backend.service.infra.ModifierCacheService;
import com.accsaber.backend.service.map.MapDifficultyComplexityService;
import com.accsaber.backend.service.map.MapDifficultyStatisticsService;
import com.accsaber.backend.service.milestone.MilestoneEvaluationService;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.player.PlayerImportService;
import com.accsaber.backend.service.songsuggest.SongSuggestService;

@ExtendWith(MockitoExtension.class)
abstract class ScoreImportTestSupport {

        static final Long STEAM_ID = 76561198012345678L;
        static final UUID NF_ID = UUID.randomUUID();
        static final Double COMPLEXITY = 8.0;
        static final Executor DIRECT = Runnable::run;

        @Mock
        BeatLeaderClient beatLeaderClient;
        @Mock
        ScoreSaberClient scoreSaberClient;
        @Mock
        ScoreService scoreService;
        @Mock
        PlayerImportService playerImportService;
        @Mock
        MapDifficultyRepository mapDifficultyRepository;
        @Mock
        MapDifficultyComplexityService mapComplexityService;
        @Mock
        ModifierCacheService modifierCacheService;
        @Mock
        ScoreRepository scoreRepository;
        @Mock
        ScoreModifierLinkRepository scoreModifierLinkRepository;
        @Mock
        ModifierRepository modifierRepository;
        @Mock
        UserRepository userRepository;
        @Mock
        MilestoneEvaluationService milestoneEvaluationService;
        @Mock
        CampaignEvaluationService campaignEvaluationService;
        @Mock
        UserCampaignRepository userCampaignRepository;
        @Mock
        CampaignRepository campaignRepository;
        @Mock
        CampaignDifficultyRepository campaignDifficultyRepository;
        @Mock
        MapDifficultyStatisticsService mapDifficultyStatisticsService;
        @Mock
        ScoreRankingService scoreRankingService;
        @Mock
        DuplicateUserService duplicateUserService;
        @Mock
        SongSuggestService songSuggestService;
        @Mock
        ScoreRecalculationService scoreRecalculationService;

        ScoreImporter importer;
        ScoreImportRecalculator recalculator;
        MapDifficulty difficulty;
        UUID categoryId;

        @BeforeEach
        void wireImporter() {
                BeatLeaderScores beatLeader = new BeatLeaderScores(beatLeaderClient, scoreRepository,
                                scoreModifierLinkRepository, modifierRepository, modifierCacheService);
                ScoreSaberScores scoreSaber = new ScoreSaberScores(scoreSaberClient, scoreRepository,
                                milestoneEvaluationService);
                importer = new ScoreImporter(beatLeader, scoreSaber, scoreService, playerImportService,
                                scoreRepository, duplicateUserService, DIRECT);
                recalculator = new ScoreImportRecalculator(scoreRankingService, mapDifficultyStatisticsService,
                                scoreRecalculationService, campaignEvaluationService, userCampaignRepository, DIRECT);

                lenient().when(modifierCacheService.getModifierCodeToId()).thenReturn(Map.of("NF", NF_ID));
                lenient().when(mapComplexityService.findActiveComplexity(any())).thenReturn(Optional.of(COMPLEXITY));
                lenient().when(duplicateUserService.resolvePrimaryUserId(anyLong()))
                                .thenAnswer(inv -> inv.getArgument(0));

                categoryId = UUID.randomUUID();
                Category category = Category.builder().id(categoryId).build();

                difficulty = MapDifficulty.builder()
                                .id(UUID.randomUUID())
                                .map(com.accsaber.backend.model.entity.map.Map.builder()
                                                .songHash("hash_123").build())
                                .difficulty(Difficulty.EXPERT_PLUS)
                                .characteristic("Standard")
                                .status(MapDifficultyStatus.RANKED)
                                .maxScore(1_000_000)
                                .blLeaderboardId("bl_123")
                                .ssLeaderboardId("ss_456")
                                .category(category)
                                .active(true)
                                .build();
        }

        BeatLeaderScoreResponse buildBlScore(String modifiers) {
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
                bl.setHmd(64);
                bl.setModifiers(modifiers);
                bl.setLeaderboardId("bl_123");
                BeatLeaderScoreResponse.Player player = new BeatLeaderScoreResponse.Player();
                player.setId(String.valueOf(STEAM_ID));
                bl.setPlayer(player);
                return bl;
        }

        ScoreSaberScoreResponse buildSsScore(List<String> mods) {
                ScoreSaberScoreResponse ss = new ScoreSaberScoreResponse();
                ss.setId(789012L);
                ss.setUnmodifiedScore(890000);
                ss.setRank(7);
                ss.setMaxCombo(480);
                ss.setBadCuts(4);
                ss.setMissedNotes(3);
                ScoreSaberScoreResponse.Device device = new ScoreSaberScoreResponse.Device();
                device.setHmd("Valve Index");
                ss.setDevice(device);
                ss.setMods(mods);
                ScoreSaberScoreResponse.Player player = new ScoreSaberScoreResponse.Player();
                player.setId(String.valueOf(STEAM_ID));
                ss.setPlayer(player);
                return ss;
        }

        ScoreSaberScoresPage buildSsPage(ScoreSaberScoreResponse... scores) {
                ScoreSaberScoresPage page = new ScoreSaberScoresPage();
                page.setData(List.of(scores));
                ScoreSaberScoresPage.Metadata meta = new ScoreSaberScoresPage.Metadata();
                meta.setTotalPages(1);
                page.setMetadata(meta);
                return page;
        }
}
