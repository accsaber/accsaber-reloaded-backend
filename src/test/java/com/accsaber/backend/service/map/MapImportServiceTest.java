package com.accsaber.backend.service.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.client.BeatLeaderClient;
import com.accsaber.backend.client.BeatSaverClient;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderLeaderboardResponse;
import com.accsaber.backend.model.dto.platform.beatsaver.BeatSaverMapResponse;
import com.accsaber.backend.model.dto.request.map.CreateMapDifficultyRequest;
import com.accsaber.backend.model.dto.request.map.ImportMapFromLeaderboardIdsRequest;
import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.CurveRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.service.score.APCalculationService;

@ExtendWith(MockitoExtension.class)
class MapImportServiceTest {

        @Mock
        private BeatLeaderClient beatLeaderClient;
        @Mock
        private BeatSaverClient beatSaverClient;
        @Mock
        private MapService mapService;
        @Mock
        private MapDifficultyComplexityService complexityService;
        @Mock
        private MapDifficultyRepository mapDifficultyRepository;
        @Mock
        private CurveRepository curveRepository;
        @Mock
        private APCalculationService apCalculationService;

        @InjectMocks
        private MapImportService mapImportService;

        private static final UUID CATEGORY_ID = UUID.randomUUID();
        private static final UUID STAFF_ID = UUID.randomUUID();

        @Nested
        class ImportByLeaderboardIds {

                @Test
                void throwsValidation_whenSsIdMissing() {
                        assertThatThrownBy(() -> mapImportService.importByLeaderboardIds(
                                        request("bl_123", null), STAFF_ID, MapDifficultyStatus.QUEUE))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("ScoreSaber");
                }

                @Test
                void throwsValidation_whenBlIdMissing() {
                        assertThatThrownBy(() -> mapImportService.importByLeaderboardIds(
                                        request(null, "ss_456"), STAFF_ID, MapDifficultyStatus.QUEUE))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("BeatLeader");
                }

                @Test
                void throwsValidation_whenBlLeaderboardNotFound() {
                        when(beatLeaderClient.getLeaderboard("bl_123")).thenReturn(Optional.empty());

                        assertThatThrownBy(() -> mapImportService.importByLeaderboardIds(
                                        request("bl_123", "ss_456"), STAFF_ID, MapDifficultyStatus.QUEUE))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("not found");
                }

                @Test
                void fetchesMetadataAndDelegatesToMapService() {
                        BeatLeaderLeaderboardResponse blLb = buildBlLeaderboard();
                        when(beatLeaderClient.getLeaderboard("bl_123")).thenReturn(Optional.of(blLb));

                        BeatSaverMapResponse bsMap = buildBeatSaverMap();
                        when(beatSaverClient.getMapByHash("abc123hash")).thenReturn(Optional.of(bsMap));

                        MapDifficultyResponse expectedResponse = MapDifficultyResponse.builder()
                                        .id(UUID.randomUUID())
                                        .status(MapDifficultyStatus.QUEUE)
                                        .build();
                        when(mapService.importMapDifficulty(any(CreateMapDifficultyRequest.class), eq(STAFF_ID),
                                        eq(MapDifficultyStatus.QUEUE)))
                                        .thenReturn(expectedResponse);

                        MapDifficultyResponse result = mapImportService.importByLeaderboardIds(
                                        request("bl_123", "ss_456"), STAFF_ID, MapDifficultyStatus.QUEUE);

                        assertThat(result).isEqualTo(expectedResponse);

                        ArgumentCaptor<CreateMapDifficultyRequest> captor = ArgumentCaptor
                                        .forClass(CreateMapDifficultyRequest.class);
                        verify(mapService).importMapDifficulty(captor.capture(), eq(STAFF_ID),
                                        eq(MapDifficultyStatus.QUEUE));

                        CreateMapDifficultyRequest created = captor.getValue();
                        assertThat(created.getSongHash()).isEqualTo("abc123hash");
                        assertThat(created.getSongName()).isEqualTo("BeatSaver Song");
                        assertThat(created.getSongAuthor()).isEqualTo("BeatSaver Author");
                        assertThat(created.getMapAuthor()).isEqualTo("BeatSaver Mapper");
                        assertThat(created.getBlLeaderboardId()).isEqualTo("bl_123");
                        assertThat(created.getSsLeaderboardId()).isEqualTo("ss_456");
                        assertThat(created.getMaxScore()).isEqualTo(1_000_000);
                        assertThat(created.getCategoryId()).isEqualTo(CATEGORY_ID);
                        assertThat(created.getDifficulty()).isEqualTo(Difficulty.EXPERT_PLUS);
                        assertThat(created.getCharacteristic()).isEqualTo("Standard");
                        assertThat(created.getBeatsaverCode()).isEqualTo("abc");
                        assertThat(created.getCoverUrl()).isEqualTo("https://cdn.beatsaver.com/cover.jpg");
                }

                @Test
                void setsComplexity_whenProvided() {
                        BeatLeaderLeaderboardResponse blLb = buildBlLeaderboard();
                        when(beatLeaderClient.getLeaderboard("bl_123")).thenReturn(Optional.of(blLb));
                        when(beatSaverClient.getMapByHash("abc123hash")).thenReturn(Optional.empty());

                        UUID diffId = UUID.randomUUID();
                        MapDifficultyResponse expectedResponse = MapDifficultyResponse.builder()
                                        .id(diffId)
                                        .status(MapDifficultyStatus.QUEUE)
                                        .build();
                        when(mapService.importMapDifficulty(any(CreateMapDifficultyRequest.class), eq(STAFF_ID),
                                        eq(MapDifficultyStatus.QUEUE)))
                                        .thenReturn(expectedResponse);

                        MapDifficulty entity = new MapDifficulty();
                        when(mapDifficultyRepository.findById(diffId)).thenReturn(Optional.of(entity));

                        ImportMapFromLeaderboardIdsRequest req = request("bl_123", "ss_456");
                        req.setComplexity(new BigDecimal("7.5"));

                        mapImportService.importByLeaderboardIds(req, STAFF_ID, MapDifficultyStatus.QUEUE);

                        verify(complexityService).setComplexity(eq(entity), eq(new BigDecimal("7.5")),
                                        eq("Initial import"), eq(null));
                }

                @Test
                void fallsBackToBlMetadata_whenBeatSaverUnavailable() {
                        BeatLeaderLeaderboardResponse blLb = buildBlLeaderboard();
                        when(beatLeaderClient.getLeaderboard("bl_123")).thenReturn(Optional.of(blLb));
                        when(beatSaverClient.getMapByHash("abc123hash")).thenReturn(Optional.empty());

                        MapDifficultyResponse expectedResponse = MapDifficultyResponse.builder()
                                        .id(UUID.randomUUID())
                                        .status(MapDifficultyStatus.QUEUE)
                                        .build();
                        when(mapService.importMapDifficulty(any(CreateMapDifficultyRequest.class), eq(STAFF_ID),
                                        eq(MapDifficultyStatus.QUEUE)))
                                        .thenReturn(expectedResponse);

                        mapImportService.importByLeaderboardIds(
                                        request("bl_123", "ss_456"), STAFF_ID, MapDifficultyStatus.QUEUE);

                        ArgumentCaptor<CreateMapDifficultyRequest> captor = ArgumentCaptor
                                        .forClass(CreateMapDifficultyRequest.class);
                        verify(mapService).importMapDifficulty(captor.capture(), eq(STAFF_ID),
                                        eq(MapDifficultyStatus.QUEUE));

                        CreateMapDifficultyRequest created = captor.getValue();
                        assertThat(created.getSongName()).isEqualTo("BL Song");
                        assertThat(created.getSongAuthor()).isEqualTo("BL Author");
                        assertThat(created.getMapAuthor()).isEqualTo("BL Mapper");
                        assertThat(created.getBeatsaverCode()).isNull();
                        assertThat(created.getCoverUrl()).isNull();
                }
        }

        private ImportMapFromLeaderboardIdsRequest request(String blId, String ssId) {
                ImportMapFromLeaderboardIdsRequest req = new ImportMapFromLeaderboardIdsRequest();
                req.setBlLeaderboardId(blId);
                req.setSsLeaderboardId(ssId);
                req.setCategoryId(CATEGORY_ID);
                req.setDifficulty(Difficulty.EXPERT_PLUS);
                req.setCharacteristic("Standard");
                return req;
        }

        private BeatLeaderLeaderboardResponse buildBlLeaderboard() {
                BeatLeaderLeaderboardResponse lb = new BeatLeaderLeaderboardResponse();
                lb.setId("bl_123");
                lb.setPlays(Long.valueOf(500));

                BeatLeaderLeaderboardResponse.Song song = new BeatLeaderLeaderboardResponse.Song();
                song.setHash("abc123hash");
                song.setName("BL Song");
                song.setAuthor("BL Author");
                song.setMapper("BL Mapper");
                lb.setSong(song);

                BeatLeaderLeaderboardResponse.DifficultyDescription diff = new BeatLeaderLeaderboardResponse.DifficultyDescription();
                diff.setDifficultyName("ExpertPlus");
                diff.setModeName("Standard");
                diff.setMaxScore(1_000_000);
                lb.setDifficulty(diff);

                return lb;
        }

        private BeatSaverMapResponse buildBeatSaverMap() {
                BeatSaverMapResponse map = new BeatSaverMapResponse();
                map.setId("abc");

                BeatSaverMapResponse.Metadata metadata = new BeatSaverMapResponse.Metadata();
                metadata.setSongName("BeatSaver Song");
                metadata.setSongAuthorName("BeatSaver Author");
                metadata.setLevelAuthorName("BeatSaver Mapper");
                map.setMetadata(metadata);

                BeatSaverMapResponse.Version version = new BeatSaverMapResponse.Version();
                version.setHash("abc123hash");
                version.setCoverURL("https://cdn.beatsaver.com/cover.jpg");
                map.setVersions(List.of(version));

                return map;
        }
}
