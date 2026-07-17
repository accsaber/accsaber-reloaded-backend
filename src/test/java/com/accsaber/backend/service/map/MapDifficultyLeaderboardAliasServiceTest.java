package com.accsaber.backend.service.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.client.BeatLeaderClient;
import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderLeaderboardResponse;
import com.accsaber.backend.model.dto.request.map.LinkLeaderboardAliasRequest;
import com.accsaber.backend.model.dto.response.map.LeaderboardAliasResponse;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.LeaderboardPlatform;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyLeaderboardAlias;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.map.MapDifficultyLeaderboardAliasRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.service.score.ScoreImportService;
import com.accsaber.backend.service.score.ScoreIngestionService;

@ExtendWith(MockitoExtension.class)
class MapDifficultyLeaderboardAliasServiceTest {

    @Mock
    private MapDifficultyLeaderboardAliasRepository aliasRepository;
    @Mock
    private MapDifficultyRepository mapDifficultyRepository;
    @Mock
    private BeatLeaderClient beatLeaderClient;
    @Mock
    private ScoreIngestionService scoreIngestionService;
    @Mock
    private ScoreImportService scoreImportService;

    @InjectMocks
    private MapDifficultyLeaderboardAliasService service;

    private static final UUID DIFFICULTY_ID = UUID.randomUUID();
    private static final int MAX_SCORE = 1_000_000;

    private MapDifficulty difficulty;

    @BeforeEach
    void setUp() {
        difficulty = MapDifficulty.builder()
                .id(DIFFICULTY_ID)
                .difficulty(Difficulty.EXPERT_PLUS)
                .characteristic("Standard")
                .status(MapDifficultyStatus.RANKED)
                .maxScore(MAX_SCORE)
                .blLeaderboardId("original_bl")
                .ssLeaderboardId("original_ss")
                .active(true)
                .build();
    }

    private LinkLeaderboardAliasRequest request(String blId, String ssId) {
        LinkLeaderboardAliasRequest req = new LinkLeaderboardAliasRequest();
        req.setBlLeaderboardId(blId);
        req.setSsLeaderboardId(ssId);
        return req;
    }

    private void stubLeaderboardMaxScore(String blId, Integer maxScore) {
        BeatLeaderLeaderboardResponse leaderboard = new BeatLeaderLeaderboardResponse();
        BeatLeaderLeaderboardResponse.DifficultyDescription desc = new BeatLeaderLeaderboardResponse.DifficultyDescription();
        desc.setMaxScore(maxScore);
        leaderboard.setDifficulty(desc);
        when(beatLeaderClient.getLeaderboard(blId)).thenReturn(Optional.of(leaderboard));
    }

    @Test
    void linkAlias_savesOneRowWithBothIds_refreshesIngestion_andBackfills() {
        when(mapDifficultyRepository.findByIdAndActiveTrue(DIFFICULTY_ID)).thenReturn(Optional.of(difficulty));
        stubLeaderboardMaxScore("combined_bl", MAX_SCORE);
        when(mapDifficultyRepository.findByBlLeaderboardId("combined_bl")).thenReturn(Optional.empty());
        when(mapDifficultyRepository.findBySsLeaderboardId("combined_ss")).thenReturn(Optional.empty());
        when(aliasRepository.findByMapDifficulty_Id(DIFFICULTY_ID)).thenReturn(List.of());

        List<LeaderboardAliasResponse> result = service.linkAlias(DIFFICULTY_ID,
                request("combined_bl", "combined_ss"), UUID.randomUUID());

        ArgumentCaptor<MapDifficultyLeaderboardAlias> captor = ArgumentCaptor
                .forClass(MapDifficultyLeaderboardAlias.class);
        verify(aliasRepository).save(captor.capture());
        assertThat(captor.getValue().getBlLeaderboardId()).isEqualTo("combined_bl");
        assertThat(captor.getValue().getSsLeaderboardId()).isEqualTo("combined_ss");
        assertThat(captor.getValue().getMapDifficulty()).isSameAs(difficulty);

        verify(scoreIngestionService).refreshRankedLeaderboardIds();
        verify(scoreImportService).backfillLeaderboardAsync(DIFFICULTY_ID, LeaderboardPlatform.BEATLEADER, "combined_bl");
        verify(scoreImportService).backfillLeaderboardAsync(DIFFICULTY_ID, LeaderboardPlatform.SCORESABER, "combined_ss");
        assertThat(result).isNotNull();
    }

    @Test
    void linkAlias_beatLeaderOnly_leavesScoreSaberNull_andSkipsSsBackfill() {
        when(mapDifficultyRepository.findByIdAndActiveTrue(DIFFICULTY_ID)).thenReturn(Optional.of(difficulty));
        stubLeaderboardMaxScore("combined_bl", MAX_SCORE);
        when(mapDifficultyRepository.findByBlLeaderboardId("combined_bl")).thenReturn(Optional.empty());
        when(aliasRepository.findByMapDifficulty_Id(DIFFICULTY_ID)).thenReturn(List.of());

        service.linkAlias(DIFFICULTY_ID, request("combined_bl", null), UUID.randomUUID());

        ArgumentCaptor<MapDifficultyLeaderboardAlias> captor = ArgumentCaptor
                .forClass(MapDifficultyLeaderboardAlias.class);
        verify(aliasRepository).save(captor.capture());
        assertThat(captor.getValue().getBlLeaderboardId()).isEqualTo("combined_bl");
        assertThat(captor.getValue().getSsLeaderboardId()).isNull();

        verify(scoreImportService).backfillLeaderboardAsync(DIFFICULTY_ID, LeaderboardPlatform.BEATLEADER, "combined_bl");
        verify(scoreImportService, never()).backfillLeaderboardAsync(any(), eq(LeaderboardPlatform.SCORESABER), any());
    }

    @Test
    void linkAlias_rejectsChartMismatch() {
        when(mapDifficultyRepository.findByIdAndActiveTrue(DIFFICULTY_ID)).thenReturn(Optional.of(difficulty));
        stubLeaderboardMaxScore("combined_bl", MAX_SCORE - 500);

        assertThatThrownBy(() -> service.linkAlias(DIFFICULTY_ID, request("combined_bl", null), UUID.randomUUID()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Chart mismatch");

        verify(aliasRepository, never()).save(any());
        verify(scoreIngestionService, never()).refreshRankedLeaderboardIds();
    }

    @Test
    void linkAlias_rejectsAlreadyUsedLeaderboard() {
        when(mapDifficultyRepository.findByIdAndActiveTrue(DIFFICULTY_ID)).thenReturn(Optional.of(difficulty));
        stubLeaderboardMaxScore("combined_bl", MAX_SCORE);
        MapDifficulty other = MapDifficulty.builder().id(UUID.randomUUID()).build();
        when(mapDifficultyRepository.findByBlLeaderboardId("combined_bl")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.linkAlias(DIFFICULTY_ID, request("combined_bl", null), UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);

        verify(aliasRepository, never()).save(any());
    }

    @Test
    void linkAlias_rejectsWhenDifficultyHasNoMaxScore() {
        difficulty.setMaxScore(null);
        when(mapDifficultyRepository.findByIdAndActiveTrue(DIFFICULTY_ID)).thenReturn(Optional.of(difficulty));

        assertThatThrownBy(() -> service.linkAlias(DIFFICULTY_ID, request("combined_bl", null), UUID.randomUUID()))
                .isInstanceOf(ValidationException.class);

        verify(beatLeaderClient, never()).getLeaderboard(any());
        verify(aliasRepository, never()).save(any());
    }

    @Test
    void unlinkAlias_rejectsWrongDifficulty() {
        UUID aliasId = UUID.randomUUID();
        MapDifficulty otherDifficulty = MapDifficulty.builder().id(UUID.randomUUID()).build();
        MapDifficultyLeaderboardAlias alias = MapDifficultyLeaderboardAlias.builder()
                .id(aliasId)
                .mapDifficulty(otherDifficulty)
                .blLeaderboardId("combined_bl")
                .build();
        when(aliasRepository.findById(aliasId)).thenReturn(Optional.of(alias));

        assertThatThrownBy(() -> service.unlinkAlias(DIFFICULTY_ID, aliasId))
                .isInstanceOf(ValidationException.class);

        verify(aliasRepository, never()).delete(any());
        verify(scoreIngestionService, never()).refreshRankedLeaderboardIds();
    }
}
