package com.accsaber.backend.service.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.client.BeatLeaderClient;
import com.accsaber.backend.client.ScoreSaberClient;
import com.accsaber.backend.model.dto.APResult;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoresPage;
import com.accsaber.backend.model.dto.response.map.LeaderboardPreviewResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyComplexityEstimate;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.map.MapDifficultyComplexityEstimateRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.score.APCalculationService;

@ExtendWith(MockitoExtension.class)
class LeaderboardPreviewServiceTest {

    @Mock
    private MapDifficultyRepository mapDifficultyRepository;
    @Mock
    private MapDifficultyComplexityService complexityService;
    @Mock
    private MapDifficultyComplexityEstimateRepository estimateRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BeatLeaderClient beatLeaderClient;
    @Mock
    private ScoreSaberClient scoreSaberClient;
    @Mock
    private APCalculationService apCalculationService;
    @InjectMocks
    private LeaderboardPreviewService service;

    private final Curve curve = Curve.builder().id(UUID.randomUUID()).build();
    private final MapDifficulty queued = MapDifficulty.builder()
            .id(UUID.randomUUID())
            .status(MapDifficultyStatus.QUEUE)
            .difficulty(Difficulty.EXPERT)
            .characteristic("Standard")
            .maxScore(1_000_000)
            .blLeaderboardId("bl1")
            .ssLeaderboardId("ss1")
            .category(Category.builder().id(UUID.randomUUID()).code("standard_acc").scoreCurve(curve).build())
            .map(Map.builder().id(UUID.randomUUID()).songName("Queued").build())
            .build();

    @Test
    void pricesTheLiveBoardsWithTheScriptEstimateWhenTheMapHasNoComplexityYet() {
        when(mapDifficultyRepository.findByIdAndActiveTrueWithMapAndCategory(queued.getId()))
                .thenReturn(Optional.of(queued));
        when(complexityService.findActiveComplexity(queued.getId())).thenReturn(Optional.empty());
        when(estimateRepository.findByMapDifficultyId(queued.getId())).thenReturn(Optional.of(
                MapDifficultyComplexityEstimate.builder().complexity(6.5).version("note-acc-2026-09-11").build()));
        when(beatLeaderClient.getLeaderboardScores("bl1", 1, 100)).thenReturn(List.of(
                bl(7L, 990_000, null), bl(8L, 995_000, "NF,SS"), bl(9L, 980_000, "")));
        ScoreSaberScoresPage ssPage = new ScoreSaberScoresPage();
        ssPage.setData(List.of(ss(7L, 999_000), ss(10L, 985_000)));
        when(scoreSaberClient.getLeaderboardScores("ss1", 1)).thenReturn(ssPage);
        when(userRepository.findAllById(any()))
                .thenReturn(List.of(User.builder().id(7L).name("Seven").country("BR").build()));
        when(apCalculationService.calculateRawAP(anyDouble(), eq(6.5), eq(curve)))
                .thenAnswer(inv -> new APResult(inv.getArgument(0, Double.class) * 1000, 0.5));

        LeaderboardPreviewResponse preview = service.preview(queued.getId(), 100);

        assertThat(preview.getComplexity()).isEqualTo(6.5);
        assertThat(preview.getComplexitySource()).isEqualTo("script note-acc-2026-09-11");
        assertThat(preview.getFetched()).isEqualTo(3);
        assertThat(preview.getRows()).extracting(LeaderboardPreviewResponse.Row::getUserId).containsExactly("7", "10",
                "9");
        LeaderboardPreviewResponse.Row top = preview.getRows().get(0);
        assertThat(top.getPlatform()).isEqualTo("BEATLEADER");
        assertThat(top.getAccuracy()).isEqualTo(0.99);
        assertThat(top.getAp()).isEqualTo(990.0);
        assertThat(top.getName()).isEqualTo("Seven");
        assertThat(preview.getRows().get(1).getName()).isEqualTo("ss-10");
        assertThat(preview.getRows().get(1).getRank()).isEqualTo(2);
    }

    @Test
    void usesTheCurrentComplexityWhenTheMapHasOne() {
        when(mapDifficultyRepository.findByIdAndActiveTrueWithMapAndCategory(queued.getId()))
                .thenReturn(Optional.of(queued));
        when(complexityService.findActiveComplexity(queued.getId())).thenReturn(Optional.of(7.0));
        when(beatLeaderClient.getLeaderboardScores("bl1", 1, 100)).thenReturn(List.of());
        ScoreSaberScoresPage ssPage = new ScoreSaberScoresPage();
        ssPage.setData(List.of());
        when(scoreSaberClient.getLeaderboardScores("ss1", 1)).thenReturn(ssPage);
        when(userRepository.findAllById(any())).thenReturn(List.of());

        LeaderboardPreviewResponse preview = service.preview(queued.getId(), 100);

        assertThat(preview.getComplexitySource()).isEqualTo("current");
        assertThat(preview.getRows()).isEmpty();
        verify(estimateRepository, never()).findByMapDifficultyId(any());
        verify(apCalculationService, never()).calculateRawAP(anyDouble(), anyDouble(), any());
    }

    private static BeatLeaderScoreResponse bl(long userId, int baseScore, String modifiers) {
        BeatLeaderScoreResponse score = new BeatLeaderScoreResponse();
        BeatLeaderScoreResponse.Player player = new BeatLeaderScoreResponse.Player();
        player.setId(String.valueOf(userId));
        player.setName("bl-" + userId);
        score.setPlayer(player);
        score.setBaseScore(baseScore);
        score.setModifiers(modifiers);
        return score;
    }

    private static ScoreSaberScoreResponse ss(long userId, int unmodifiedScore) {
        ScoreSaberScoreResponse score = new ScoreSaberScoreResponse();
        ScoreSaberScoreResponse.Player player = new ScoreSaberScoreResponse.Player();
        player.setId(String.valueOf(userId));
        player.setName("ss-" + userId);
        score.setPlayer(player);
        score.setUnmodifiedScore(unmodifiedScore);
        score.setMods(List.of());
        return score;
    }
}
