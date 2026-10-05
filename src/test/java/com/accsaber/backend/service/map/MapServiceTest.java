package com.accsaber.backend.service.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.request.map.CreateMapDifficultyRequest;
import com.accsaber.backend.model.dto.request.map.UpdateMapStatusRequest;
import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.map.MapRepository;
import com.accsaber.backend.repository.map.StaffMapVoteRepository;
import com.accsaber.backend.repository.staff.StaffUserRepository;
import com.accsaber.backend.service.playlist.PlaylistService;
import com.accsaber.backend.service.score.ScoreIngestionService;

@ExtendWith(MockitoExtension.class)
class MapServiceTest {

        @Mock
        private MapRepository mapRepository;

        @Mock
        private MapDifficultyRepository mapDifficultyRepository;

        @Mock
        private CategoryRepository categoryRepository;

        @Mock
        private StaffUserRepository staffUserRepository;

        @Mock
        private MapDifficultyComplexityService complexityService;

        @Mock
        private MapDifficultyStatisticsService statisticsService;

        @Mock
        private StaffMapVoteRepository voteRepository;

        @Mock
        private com.accsaber.backend.repository.map.MapDifficultyComplexityEstimateRepository estimateRepository;

        @Mock
        private ScoreIngestionService scoreIngestionService;
        @Mock
        private com.accsaber.backend.service.score.CampaignScoreGate campaignScoreGate;

        @Mock
        private PlaylistService playlistService;

        @InjectMocks
        private MapService mapService;

        @Nested
        class FindById {

                @Test
                void throwsNotFound_whenMapDoesNotExist() {
                        UUID id = UUID.randomUUID();
                        when(mapRepository.findByIdAndActiveTrue(id)).thenReturn(Optional.empty());

                        assertThatThrownBy(() -> mapService.findById(id))
                                        .isInstanceOf(ResourceNotFoundException.class);
                }
        }

        @Nested
        class ImportMapDifficulty {

                private final UUID staffId = UUID.randomUUID();

                @Test
                void createsNewMap_whenSongHashNotFound() {
                        Category category = buildCategory();
                        CreateMapDifficultyRequest request = buildRequest(category.getId());
                        Map savedMap = buildMap();
                        when(mapRepository.findBySongHashAndActiveTrue(request.getSongHash()))
                                        .thenReturn(Optional.empty());
                        when(mapRepository.save(any())).thenReturn(savedMap);
                        when(mapDifficultyRepository.findByMapIdAndDifficultyAndCharacteristicAndActiveTrue(
                                        any(), any(), any())).thenReturn(Optional.empty());
                        when(categoryRepository.findByIdAndActiveTrue(category.getId()))
                                        .thenReturn(Optional.of(category));
                        when(mapDifficultyRepository.save(any())).thenAnswer(inv -> {
                                MapDifficulty d = inv.getArgument(0);
                                return MapDifficulty.builder()
                                                .id(UUID.randomUUID())
                                                .map(d.getMap())
                                                .category(d.getCategory())
                                                .difficulty(d.getDifficulty())
                                                .characteristic(d.getCharacteristic())
                                                .status(d.getStatus())
                                                .maxScore(d.getMaxScore())
                                                .active(true)
                                                .build();
                        });

                        MapDifficultyResponse response = mapService.importMapDifficulty(request, staffId);

                        assertThat(response.getDifficulty()).isEqualTo(Difficulty.EXPERT_PLUS);
                        assertThat(response.getStatus()).isEqualTo(MapDifficultyStatus.QUEUE);
                        verify(mapRepository).save(any(Map.class));
                }

                @Test
                void reusesExistingMap_whenSongHashAlreadyExists() {
                        Map existingMap = buildMap();
                        Category category = buildCategory();
                        CreateMapDifficultyRequest request = buildRequest(category.getId());
                        when(mapRepository.findBySongHashAndActiveTrue(request.getSongHash()))
                                        .thenReturn(Optional.of(existingMap));
                        when(mapDifficultyRepository.findByMapIdAndDifficultyAndCharacteristicAndActiveTrue(
                                        existingMap.getId(), request.getDifficulty(), request.getCharacteristic()))
                                        .thenReturn(Optional.empty());
                        when(categoryRepository.findByIdAndActiveTrue(category.getId()))
                                        .thenReturn(Optional.of(category));
                        when(mapDifficultyRepository.save(any())).thenAnswer(inv -> {
                                MapDifficulty d = inv.getArgument(0);
                                return MapDifficulty.builder()
                                                .id(UUID.randomUUID())
                                                .map(d.getMap())
                                                .category(d.getCategory())
                                                .difficulty(d.getDifficulty())
                                                .characteristic(d.getCharacteristic())
                                                .status(d.getStatus())
                                                .maxScore(d.getMaxScore())
                                                .active(true)
                                                .build();
                        });

                        mapService.importMapDifficulty(request, staffId);

                        verify(mapRepository, never()).save(any(Map.class));
                }

                @Test
                void throwsConflict_whenSameLeaderboardIdsAlreadyExist() {
                        Map existingMap = buildMap();
                        Category category = buildCategory();
                        CreateMapDifficultyRequest request = buildRequest(category.getId());
                        MapDifficulty existing = buildDifficulty(existingMap, category, MapDifficultyStatus.QUEUE);
                        when(mapRepository.findBySongHashAndActiveTrue(request.getSongHash()))
                                        .thenReturn(Optional.of(existingMap));
                        when(mapDifficultyRepository.findByMapIdAndDifficultyAndCharacteristicAndActiveTrue(
                                        existingMap.getId(), request.getDifficulty(), request.getCharacteristic()))
                                        .thenReturn(Optional.of(existing));

                        assertThatThrownBy(() -> mapService.importMapDifficulty(request, staffId))
                                        .isInstanceOf(ConflictException.class);
                }

                @Test
                void coexistsAlongsideRankedVersion_whenLeaderboardIdsDiffer() {
                        Map existingMap = buildMap();
                        Category category = buildCategory();
                        CreateMapDifficultyRequest request = buildRequestWithLeaderboards(category.getId(),
                                        "new-bl-id", "new-ss-id");
                        MapDifficulty ranked = buildDifficulty(existingMap, category, MapDifficultyStatus.RANKED);
                        when(mapRepository.findBySongHashAndActiveTrue(request.getSongHash()))
                                        .thenReturn(Optional.of(existingMap));
                        when(mapDifficultyRepository.findByMapIdAndDifficultyAndCharacteristicAndActiveTrue(
                                        existingMap.getId(), request.getDifficulty(), request.getCharacteristic()))
                                        .thenReturn(Optional.of(ranked));
                        when(categoryRepository.findByIdAndActiveTrue(category.getId()))
                                        .thenReturn(Optional.of(category));
                        when(mapDifficultyRepository.save(any())).thenAnswer(inv -> {
                                MapDifficulty d = inv.getArgument(0);
                                return MapDifficulty.builder()
                                                .id(UUID.randomUUID())
                                                .map(d.getMap())
                                                .category(d.getCategory())
                                                .difficulty(d.getDifficulty())
                                                .characteristic(d.getCharacteristic())
                                                .status(d.getStatus())
                                                .previousVersion(d.getPreviousVersion())
                                                .active(true)
                                                .build();
                        });

                        MapDifficultyResponse response = mapService.importMapDifficulty(request, staffId);

                        assertThat(response.getPreviousVersionId()).isEqualTo(ranked.getId());
                        verify(mapDifficultyRepository, never()).save(ranked);
                }

        }

        @Nested
        class UpdateStatus {

                @Test
                void setsRankedAt_whenStatusBecomesRanked() {
                        MapDifficulty diff = buildStandaloneDifficulty(MapDifficultyStatus.QUALIFIED);
                        when(mapDifficultyRepository.findByIdAndActiveTrue(diff.getId()))
                                        .thenReturn(Optional.of(diff));
                        when(mapDifficultyRepository.save(any())).thenReturn(diff);
                        when(complexityService.findActiveComplexity(diff.getId()))
                                        .thenReturn(Optional.of((double) (8.0)));
                        when(statisticsService.findActive(diff.getId())).thenReturn(Optional.empty());

                        UpdateMapStatusRequest request = new UpdateMapStatusRequest();
                        request.setStatus(MapDifficultyStatus.RANKED);
                        mapService.updateStatus(diff.getId(), request);

                        assertThat(diff.getStatus()).isEqualTo(MapDifficultyStatus.RANKED);
                        assertThat(diff.getRankedAt()).isNotNull();
                }

                @Test
                void clearsRankedAt_whenStatusBecomesQualified() {
                        MapDifficulty diff = buildStandaloneDifficulty(MapDifficultyStatus.RANKED);
                        diff.setRankedAt(Instant.now());
                        when(mapDifficultyRepository.findByIdAndActiveTrue(diff.getId()))
                                        .thenReturn(Optional.of(diff));
                        when(mapDifficultyRepository.save(any())).thenReturn(diff);
                        when(complexityService.findActiveComplexity(diff.getId())).thenReturn(Optional.empty());
                        when(statisticsService.findActive(diff.getId())).thenReturn(Optional.empty());

                        UpdateMapStatusRequest request = new UpdateMapStatusRequest();
                        request.setStatus(MapDifficultyStatus.QUALIFIED);
                        mapService.updateStatus(diff.getId(), request);

                        assertThat(diff.getStatus()).isEqualTo(MapDifficultyStatus.QUALIFIED);
                        assertThat(diff.getRankedAt()).isNull();
                }
        }

        private Map buildMap() {
                return Map.builder()
                                .id(UUID.randomUUID())
                                .songName("Song")
                                .songAuthor("Author")
                                .songHash("abc123")
                                .mapAuthor("Mapper")
                                .active(true)
                                .build();
        }

        private Category buildCategory() {
                return Category.builder()
                                .id(UUID.randomUUID())
                                .code("true_acc")
                                .name("True Acc")
                                .description("True Acc Category")
                                .countForOverall(true)
                                .active(true)
                                .build();
        }

        private MapDifficulty buildDifficulty(Map map, Category category, MapDifficultyStatus status) {
                return MapDifficulty.builder()
                                .id(UUID.randomUUID())
                                .map(map)
                                .category(category)
                                .difficulty(Difficulty.EXPERT_PLUS)
                                .characteristic("Standard")
                                .status(status)
                                .maxScore(1_000_000)
                                .active(true)
                                .build();
        }

        private MapDifficulty buildStandaloneDifficulty(MapDifficultyStatus status) {
                return MapDifficulty.builder()
                                .id(UUID.randomUUID())
                                .map(buildMap())
                                .category(buildCategory())
                                .difficulty(Difficulty.EXPERT_PLUS)
                                .characteristic("Standard")
                                .status(status)
                                .maxScore(1_000_000)
                                .active(true)
                                .build();
        }

        private CreateMapDifficultyRequest buildRequest(UUID categoryId) {
                return buildRequestWithLeaderboards(categoryId, null, null);
        }

        private CreateMapDifficultyRequest buildRequestWithLeaderboards(UUID categoryId,
                        String blLeaderboardId, String ssLeaderboardId) {
                CreateMapDifficultyRequest request = new CreateMapDifficultyRequest();
                request.setSongName("Song");
                request.setSongAuthor("Author");
                request.setSongHash("abc123");
                request.setMapAuthor("Mapper");
                request.setCategoryId(categoryId);
                request.setDifficulty(Difficulty.EXPERT_PLUS);
                request.setCharacteristic("Standard");
                request.setMaxScore(1_000_000);
                request.setBlLeaderboardId(blLeaderboardId);
                request.setSsLeaderboardId(ssLeaderboardId);
                return request;
        }

        @Nested
        class FindDifficultiesActiveFilter {

                private final PageRequest pageable = PageRequest.of(0, 20);

                @SuppressWarnings("unchecked")
                private java.util.Collection<MapDifficultyStatus> capturedStatuses(boolean active,
                                List<MapDifficultyStatus> requested) {
                        when(mapDifficultyRepository.findWithComplexityFilters(any(), any(Boolean.class), any(), any(),
                                        any(), any(), any(), any()))
                                        .thenReturn(new PageImpl<>(List.of(), pageable, 0));

                        mapService.findDifficulties(null, null, requested, null, null, null, null, active, pageable);

                        ArgumentCaptor<java.util.Collection<MapDifficultyStatus>> captor = ArgumentCaptor
                                        .forClass(java.util.Collection.class);
                        verify(mapDifficultyRepository).findWithComplexityFilters(any(), any(Boolean.class), any(),
                                        captor.capture(), any(), any(), any(), any());
                        return captor.getValue();
                }

                @Test
                void activeListLeavesStatusesNullSoCampaignImportsStayHidden() {
                        assertThat(capturedStatuses(true, null)).isNull();
                }

                @Test
                void deactivatedListAsksForEveryStatusSoCampaignImportsAreVisible() {
                        assertThat(capturedStatuses(false, null))
                                        .containsExactlyInAnyOrder(MapDifficultyStatus.values());
                }

                @Test
                void explicitStatusesArePassedThroughUntouched() {
                        assertThat(capturedStatuses(false, List.of(MapDifficultyStatus.RANKED)))
                                        .containsExactly(MapDifficultyStatus.RANKED);
                }
        }
}
