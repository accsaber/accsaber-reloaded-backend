package com.accsaber.backend.service.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.request.map.CreateMapDifficultyRequest;
import com.accsaber.backend.model.dto.request.map.UpdateMapComplexityRequest;
import com.accsaber.backend.model.dto.request.map.UpdateMapStatusRequest;
import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.model.dto.response.map.MapResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.map.MapRepository;
import com.accsaber.backend.repository.staff.StaffUserRepository;

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

        @InjectMocks
        private MapService mapService;

        @Nested
        class FindAll {

                @Test
                void returnsEmptyPage_whenNoMapsMatchFilters() {
                        when(mapRepository.findByDifficultyFilters(null, null, PageRequest.of(0, 20)))
                                        .thenReturn(Page.empty());

                        Page<MapResponse> result = mapService.findAll(null, null, PageRequest.of(0, 20));

                        assertThat(result).isEmpty();
                }

                @Test
                void returnsMapsWithEnrichedDifficulties() {
                        Map map = buildMap();
                        Category category = buildCategory();
                        MapDifficulty diff = buildDifficulty(map, category);
                        Page<Map> mapPage = new PageImpl<>(List.of(map));
                        when(mapRepository.findByDifficultyFilters(null, null, PageRequest.of(0, 20)))
                                        .thenReturn(mapPage);
                        when(mapDifficultyRepository.findByMapIdsWithFilters(List.of(map.getId()), null, null))
                                        .thenReturn(List.of(diff));
                        when(complexityService.findActiveComplexitiesForDifficulties(any()))
                                        .thenReturn(java.util.Map.of());
                        when(statisticsService.findActiveForDifficulties(any()))
                                        .thenReturn(java.util.Map.of());

                        Page<MapResponse> result = mapService.findAll(null, null, PageRequest.of(0, 20));

                        assertThat(result).hasSize(1);
                        assertThat(result.getContent().get(0).getSongName()).isEqualTo("Song");
                        assertThat(result.getContent().get(0).getDifficulties()).hasSize(1);
                }
        }

        @Nested
        class FindById {

                @Test
                void returnsMapResponseWithDifficulties() {
                        Map map = buildMap();
                        Category category = buildCategory();
                        MapDifficulty diff = buildDifficulty(map, category);
                        when(mapRepository.findByIdAndActiveTrue(map.getId())).thenReturn(Optional.of(map));
                        when(mapDifficultyRepository.findByMapIdAndActiveTrue(map.getId())).thenReturn(List.of(diff));
                        when(complexityService.findActiveComplexitiesForDifficulties(any()))
                                        .thenReturn(java.util.Map.of());
                        when(statisticsService.findActiveForDifficulties(any()))
                                        .thenReturn(java.util.Map.of());

                        MapResponse response = mapService.findById(map.getId());

                        assertThat(response.getId()).isEqualTo(map.getId());
                        assertThat(response.getSongName()).isEqualTo("Song");
                        assertThat(response.getDifficulties()).hasSize(1);
                }

                @Test
                void throwsNotFound_whenMapDoesNotExist() {
                        UUID id = UUID.randomUUID();
                        when(mapRepository.findByIdAndActiveTrue(id)).thenReturn(Optional.empty());

                        assertThatThrownBy(() -> mapService.findById(id))
                                        .isInstanceOf(ResourceNotFoundException.class);
                }
        }

        @Nested
        class FindDifficultiesByMapId {

                @Test
                void returnsDifficulties_forExistingMap() {
                        Map map = buildMap();
                        Category category = buildCategory();
                        MapDifficulty diff = buildDifficulty(map, category);
                        when(mapRepository.existsById(map.getId())).thenReturn(true);
                        when(mapDifficultyRepository.findByMapIdAndActiveTrue(map.getId())).thenReturn(List.of(diff));
                        when(complexityService.findActiveComplexitiesForDifficulties(any()))
                                        .thenReturn(java.util.Map.of());
                        when(statisticsService.findActiveForDifficulties(any()))
                                        .thenReturn(java.util.Map.of());

                        List<MapDifficultyResponse> result = mapService.findDifficultiesByMapId(map.getId());

                        assertThat(result).hasSize(1);
                        assertThat(result.get(0).getDifficulty()).isEqualTo(Difficulty.EXPERT_PLUS);
                }

                @Test
                void throwsNotFound_whenMapDoesNotExist() {
                        UUID id = UUID.randomUUID();
                        when(mapRepository.existsById(id)).thenReturn(false);

                        assertThatThrownBy(() -> mapService.findDifficultiesByMapId(id))
                                        .isInstanceOf(ResourceNotFoundException.class);
                }

                @Test
                void returnsEmptyList_whenMapHasNoDifficulties() {
                        Map map = buildMap();
                        when(mapRepository.existsById(map.getId())).thenReturn(true);
                        when(mapDifficultyRepository.findByMapIdAndActiveTrue(map.getId())).thenReturn(List.of());

                        List<MapDifficultyResponse> result = mapService.findDifficultiesByMapId(map.getId());

                        assertThat(result).isEmpty();
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

                @Test
                void throwsNotFound_whenCategoryDoesNotExist() {
                        Map existingMap = buildMap();
                        UUID categoryId = UUID.randomUUID();
                        CreateMapDifficultyRequest request = buildRequest(categoryId);
                        when(mapRepository.findBySongHashAndActiveTrue(request.getSongHash()))
                                        .thenReturn(Optional.of(existingMap));
                        when(mapDifficultyRepository.findByMapIdAndDifficultyAndCharacteristicAndActiveTrue(
                                        existingMap.getId(), request.getDifficulty(), request.getCharacteristic()))
                                        .thenReturn(Optional.empty());
                        when(categoryRepository.findByIdAndActiveTrue(categoryId)).thenReturn(Optional.empty());

                        assertThatThrownBy(() -> mapService.importMapDifficulty(request, staffId))
                                        .isInstanceOf(ResourceNotFoundException.class);
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
                        when(complexityService.findActiveComplexity(diff.getId())).thenReturn(Optional.empty());
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

                @Test
                void throwsNotFound_whenDifficultyDoesNotExist() {
                        UUID id = UUID.randomUUID();
                        when(mapDifficultyRepository.findByIdAndActiveTrue(id)).thenReturn(Optional.empty());

                        UpdateMapStatusRequest request = new UpdateMapStatusRequest();
                        request.setStatus(MapDifficultyStatus.QUALIFIED);

                        assertThatThrownBy(() -> mapService.updateStatus(id, request))
                                        .isInstanceOf(ResourceNotFoundException.class);
                }
        }

        @Nested
        class UpdateComplexity {

                @Test
                void delegatesToComplexityService_andReturnsUpdatedComplexity() {
                        MapDifficulty diff = buildStandaloneDifficulty(MapDifficultyStatus.RANKED);
                        when(mapDifficultyRepository.findByIdAndActiveTrue(diff.getId()))
                                        .thenReturn(Optional.of(diff));
                        when(complexityService.setComplexity(diff, new BigDecimal("8.5"), "Reweight", 1L))
                                        .thenReturn(new BigDecimal("8.5"));
                        when(statisticsService.findActive(diff.getId())).thenReturn(Optional.empty());

                        UpdateMapComplexityRequest request = new UpdateMapComplexityRequest();
                        request.setComplexity(new BigDecimal("8.5"));
                        request.setReason("Reweight");
                        MapDifficultyResponse response = mapService.updateComplexity(diff.getId(), request, 1L);

                        assertThat(response.getComplexity()).isEqualByComparingTo(new BigDecimal("8.5"));
                        verify(complexityService).setComplexity(diff, new BigDecimal("8.5"), "Reweight", 1L);
                }

                @Test
                void throwsNotFound_whenDifficultyDoesNotExist() {
                        UUID id = UUID.randomUUID();
                        when(mapDifficultyRepository.findByIdAndActiveTrue(id)).thenReturn(Optional.empty());

                        UpdateMapComplexityRequest request = new UpdateMapComplexityRequest();
                        request.setComplexity(new BigDecimal("8.5"));
                        request.setReason("Reweight");

                        assertThatThrownBy(() -> mapService.updateComplexity(id, request, 1L))
                                        .isInstanceOf(ResourceNotFoundException.class);
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

        private MapDifficulty buildDifficulty(Map map, Category category) {
                return buildDifficulty(map, category, MapDifficultyStatus.RANKED);
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
}
