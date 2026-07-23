package com.accsaber.backend.service.score;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.client.BeatLeaderClient;
import com.accsaber.backend.client.ScoreSaberClient;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderScoreResponse;
import com.accsaber.backend.model.entity.campaign.CampaignDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.campaign.CampaignDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.service.campaign.CampaignEvaluationService;
import com.accsaber.backend.service.infra.ModifierCacheService;
import com.accsaber.backend.service.player.DuplicateUserService;

@ExtendWith(MockitoExtension.class)
class LegacyCampaignBackfillTest {

    private static final Long USER = 76561198087536397L;
    private static final UUID CAMPAIGN = UUID.randomUUID();

    @Mock
    private DuplicateUserService duplicateUserService;
    @Mock
    private ModifierCacheService modifierCacheService;
    @Mock
    private CampaignDifficultyRepository campaignDifficultyRepository;
    @Mock
    private ScoreRepository scoreRepository;
    @Mock
    private BeatLeaderClient beatLeaderClient;
    @Mock
    private ScoreSaberClient scoreSaberClient;
    @Mock
    private ScoreService scoreService;
    @Mock
    private CampaignEvaluationService campaignEvaluationService;

    @InjectMocks
    private ScoreImportService scoreImportService;

    @Test
    void backfillsUnrankedMapsQueryingBothPlatformsThenSettles() {
        MapDifficulty ranked = md(MapDifficultyStatus.RANKED, "bl-ranked", "ss-ranked");
        MapDifficulty alreadyHas = md(MapDifficultyStatus.CAMPAIGN, "bl-has", "ss-has");
        MapDifficulty toFetch = md(MapDifficultyStatus.CAMPAIGN, "bl-fetch", "ss-fetch");
        MapDifficulty barrierMap = md(MapDifficultyStatus.CAMPAIGN, "bl-barrier", "ss-barrier");

        when(duplicateUserService.resolvePrimaryUserId(USER)).thenReturn(USER);
        when(modifierCacheService.getModifierCodeToId()).thenReturn(Map.of());
        when(campaignDifficultyRepository.findActiveWithMapByCampaignId(CAMPAIGN))
                .thenReturn(List.of(node(ranked, false), node(alreadyHas, false),
                        node(toFetch, false), node(barrierMap, true)));

        when(scoreRepository.findEligibleCampaignRows(eq(USER), eq(List.of(alreadyHas.getId())), any()))
                .thenReturn(List.of(new com.accsaber.backend.model.entity.score.Score()));
        when(scoreRepository.findEligibleCampaignRows(eq(USER), eq(List.of(toFetch.getId())), any()))
                .thenReturn(List.of());
        when(beatLeaderClient.getPlayerScoreOnLeaderboard(String.valueOf(USER), "bl-fetch"))
                .thenReturn(Optional.of(blScore(900_000)));
        when(scoreSaberClient.getPlayerScoreOnLeaderboard(String.valueOf(USER), "ss-fetch"))
                .thenReturn(Optional.empty());

        scoreImportService.backfillAndSettleLegacyCampaign(USER, CAMPAIGN);

        verify(beatLeaderClient).getPlayerScoreOnLeaderboard(String.valueOf(USER), "bl-fetch");
        verify(scoreSaberClient).getPlayerScoreOnLeaderboard(String.valueOf(USER), "ss-fetch");
        verify(beatLeaderClient, never()).getPlayerScoreOnLeaderboard(anyString(), eq("bl-ranked"));
        verify(scoreSaberClient, never()).getPlayerScoreOnLeaderboard(anyString(), eq("ss-ranked"));
        verify(beatLeaderClient, never()).getPlayerScoreOnLeaderboard(anyString(), eq("bl-has"));
        verify(scoreSaberClient, never()).getPlayerScoreOnLeaderboard(anyString(), eq("ss-has"));
        verify(beatLeaderClient, never()).getPlayerScoreOnLeaderboard(anyString(), eq("bl-barrier"));
        verify(scoreService, times(1)).recordCampaignBackfillScore(any());
        verify(campaignEvaluationService).importLegacyScores(USER, CAMPAIGN);
    }

    private static MapDifficulty md(MapDifficultyStatus status, String blLeaderboardId, String ssLeaderboardId) {
        return MapDifficulty.builder()
                .id(UUID.randomUUID())
                .status(status)
                .blLeaderboardId(blLeaderboardId)
                .ssLeaderboardId(ssLeaderboardId)
                .build();
    }

    private static CampaignDifficulty node(MapDifficulty md, boolean barrier) {
        return CampaignDifficulty.builder().id(UUID.randomUUID()).mapDifficulty(md).barrier(barrier).build();
    }

    private static BeatLeaderScoreResponse blScore(int baseScore) {
        BeatLeaderScoreResponse bl = new BeatLeaderScoreResponse();
        bl.setBaseScore(baseScore);
        return bl;
    }
}
