package com.accsaber.backend.service.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.map.ApproveReweightRequest;
import com.accsaber.backend.model.dto.request.map.CreateBatchRequest;
import com.accsaber.backend.model.dto.request.map.UpdateBatchStatusRequest;
import com.accsaber.backend.model.dto.request.map.UpdateMapComplexityRequest;
import com.accsaber.backend.model.dto.response.map.BatchResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.Batch;
import com.accsaber.backend.model.entity.map.BatchStatus;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.repository.map.BatchRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.staff.StaffUserRepository;
import com.accsaber.backend.service.playlist.PlaylistService;
import com.accsaber.backend.service.score.ScoreImportService;
import com.accsaber.backend.service.score.ScoreIngestionService;
import com.accsaber.backend.service.score.ScoreRecalculationService;

@ExtendWith(MockitoExtension.class)
class BatchServiceTest {

        @Mock
        private BatchRepository batchRepository;

        @Mock
        private MapDifficultyRepository mapDifficultyRepository;

        @Mock
        private StaffUserRepository staffUserRepository;

        @Mock
        private MapDifficultyComplexityService complexityService;

        @Mock
        private MapDifficultyStatisticsService statisticsService;

        @Mock
        private ScoreImportService scoreImportService;

        @Mock
        private ScoreIngestionService scoreIngestionService;

        @Mock
        private ScoreRecalculationService scoreRecalculationService;

        @Mock
        private MapService mapService;

        @Mock
        private PlaylistService playlistService;

        @InjectMocks
        private BatchService batchService;

        @Nested
        class FindById {

                @Test
                void returnsBatchWithDifficulties() {
                        Batch batch = buildBatch(BatchStatus.DRAFT);
                        MapDifficulty diff = buildDifficulty(batch);
                        when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
                        when(mapDifficultyRepository.findByBatch_IdAndActiveTrue(batch.getId()))
                                        .thenReturn(List.of(diff));
                        when(complexityService.findActiveComplexitiesForDifficulties(any()))
                                        .thenReturn(java.util.Map.of());
                        when(statisticsService.findActiveForDifficulties(any()))
                                        .thenReturn(java.util.Map.of());

                        BatchResponse response = batchService.findById(batch.getId());

                        assertThat(response.getId()).isEqualTo(batch.getId());
                        assertThat(response.getName()).isEqualTo("Test Batch");
                        assertThat(response.getDifficulties()).hasSize(1);
                }

                @Test
                void throwsNotFound_whenBatchDoesNotExist() {
                        UUID id = UUID.randomUUID();
                        when(batchRepository.findById(id)).thenReturn(Optional.empty());

                        assertThatThrownBy(() -> batchService.findById(id))
                                        .isInstanceOf(ResourceNotFoundException.class);
                }
        }

        @Nested
        class FindAll {

                @Test
                void returnsPagedBatches() {
                        Batch batch = buildBatch(BatchStatus.DRAFT);
                        Page<Batch> page = new PageImpl<>(List.of(batch));
                        when(batchRepository.findAll(PageRequest.of(0, 20))).thenReturn(page);
                        when(mapDifficultyRepository.findByBatch_IdAndActiveTrue(batch.getId()))
                                        .thenReturn(List.of());

                        Page<BatchResponse> result = batchService.findAll(null, PageRequest.of(0, 20));

                        assertThat(result).hasSize(1);
                        assertThat(result.getContent().get(0).getStatus()).isEqualTo(BatchStatus.DRAFT);
                }

        }

        @Nested
        class Create {

                @Test
                void createsBatchWithDraftStatus() {
                        UUID staffId = UUID.randomUUID();
                        StaffUser staffUser = StaffUser.builder().id(staffId).build();
                        when(staffUserRepository.findById(staffId)).thenReturn(Optional.of(staffUser));

                        CreateBatchRequest request = new CreateBatchRequest();
                        request.setName("New Batch");
                        request.setDescription("A test batch");
                        when(batchRepository.save(any())).thenAnswer(inv -> {
                                Batch b = inv.getArgument(0);
                                return Batch.builder()
                                                .id(UUID.randomUUID())
                                                .name(b.getName())
                                                .description(b.getDescription())
                                                .status(BatchStatus.DRAFT)
                                                .build();
                        });

                        BatchResponse response = batchService.create(request, staffId);

                        assertThat(response.getName()).isEqualTo("New Batch");
                        assertThat(response.getStatus()).isEqualTo(BatchStatus.DRAFT);
                        assertThat(response.getDifficulties()).isEmpty();
                }
        }

        @Nested
        class UpdateStatus {

                @Test
                void updatesStatusToDraftFromReleaseReady() {
                        Batch batch = buildBatch(BatchStatus.RELEASE_READY);
                        when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
                        when(batchRepository.save(any())).thenReturn(batch);
                        when(mapDifficultyRepository.findByBatch_IdAndActiveTrue(batch.getId()))
                                        .thenReturn(List.of());

                        UpdateBatchStatusRequest request = new UpdateBatchStatusRequest();
                        request.setStatus(BatchStatus.DRAFT);
                        batchService.updateStatus(batch.getId(), request);

                        assertThat(batch.getStatus()).isEqualTo(BatchStatus.DRAFT);
                }

                @Test
                void throwsValidation_whenTryingToSetReleasedViaUpdateStatus() {
                        Batch batch = buildBatch(BatchStatus.DRAFT);
                        when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));

                        UpdateBatchStatusRequest request = new UpdateBatchStatusRequest();
                        request.setStatus(BatchStatus.RELEASED);

                        assertThatThrownBy(() -> batchService.updateStatus(batch.getId(), request))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("release endpoint");
                }

                @Test
                void throwsValidation_whenBatchIsAlreadyReleased() {
                        Batch batch = buildBatch(BatchStatus.RELEASED);
                        when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));

                        UpdateBatchStatusRequest request = new UpdateBatchStatusRequest();
                        request.setStatus(BatchStatus.DRAFT);

                        assertThatThrownBy(() -> batchService.updateStatus(batch.getId(), request))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("released");
                }

                @Test
                void throwsNotFound_whenBatchDoesNotExist() {
                        UUID id = UUID.randomUUID();
                        when(batchRepository.findById(id)).thenReturn(Optional.empty());

                        UpdateBatchStatusRequest request = new UpdateBatchStatusRequest();
                        request.setStatus(BatchStatus.RELEASE_READY);

                        assertThatThrownBy(() -> batchService.updateStatus(id, request))
                                        .isInstanceOf(ResourceNotFoundException.class);
                }
        }

        @Nested
        class AddDifficulty {

                @Test
                void assignsDifficultyToBatch() {
                        Batch batch = buildBatch(BatchStatus.DRAFT);
                        MapDifficulty diff = buildStandaloneDifficulty();
                        when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
                        when(mapDifficultyRepository.findByIdAndActiveTrue(diff.getId()))
                                        .thenReturn(Optional.of(diff));
                        when(mapDifficultyRepository.save(any())).thenReturn(diff);
                        when(mapDifficultyRepository.findByBatch_IdAndActiveTrue(batch.getId()))
                                        .thenReturn(List.of(diff));
                        when(complexityService.findActiveComplexitiesForDifficulties(any()))
                                        .thenReturn(java.util.Map.of());
                        when(statisticsService.findActiveForDifficulties(any()))
                                        .thenReturn(java.util.Map.of());

                        batchService.addDifficulty(batch.getId(), diff.getId());

                        assertThat(diff.getBatch()).isEqualTo(batch);
                        verify(mapDifficultyRepository).save(diff);
                }

                @Test
                void throwsConflict_whenDifficultyIsAlreadyInAnotherBatch() {
                        Batch batch = buildBatch(BatchStatus.DRAFT);
                        Batch otherBatch = buildBatch(BatchStatus.DRAFT);
                        MapDifficulty diff = buildStandaloneDifficulty();
                        diff.setBatch(otherBatch);
                        when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
                        when(mapDifficultyRepository.findByIdAndActiveTrue(diff.getId()))
                                        .thenReturn(Optional.of(diff));

                        assertThatThrownBy(() -> batchService.addDifficulty(batch.getId(), diff.getId()))
                                        .isInstanceOf(ConflictException.class);
                }

                @Test
                void throwsValidation_whenBatchIsReleased() {
                        Batch batch = buildBatch(BatchStatus.RELEASED);
                        when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));

                        assertThatThrownBy(() -> batchService.addDifficulty(batch.getId(), UUID.randomUUID()))
                                        .isInstanceOf(ValidationException.class);
                }

                @Test
                void throwsNotFound_whenDifficultyDoesNotExist() {
                        Batch batch = buildBatch(BatchStatus.DRAFT);
                        UUID diffId = UUID.randomUUID();
                        when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
                        when(mapDifficultyRepository.findByIdAndActiveTrue(diffId)).thenReturn(Optional.empty());

                        assertThatThrownBy(() -> batchService.addDifficulty(batch.getId(), diffId))
                                        .isInstanceOf(ResourceNotFoundException.class);
                }
        }

        @Nested
        class RemoveDifficulty {

                @Test
                void unassignsDifficultyFromBatch() {
                        Batch batch = buildBatch(BatchStatus.DRAFT);
                        MapDifficulty diff = buildDifficulty(batch);
                        when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
                        when(mapDifficultyRepository.findByIdAndActiveTrue(diff.getId()))
                                        .thenReturn(Optional.of(diff));
                        when(mapDifficultyRepository.save(any())).thenReturn(diff);
                        when(mapDifficultyRepository.findByBatch_IdAndActiveTrue(batch.getId()))
                                        .thenReturn(List.of());

                        batchService.removeDifficulty(batch.getId(), diff.getId());

                        assertThat(diff.getBatch()).isNull();
                        verify(mapDifficultyRepository).save(diff);
                }

                @Test
                void throwsValidation_whenBatchIsReleased() {
                        Batch batch = buildBatch(BatchStatus.RELEASED);
                        when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));

                        assertThatThrownBy(() -> batchService.removeDifficulty(batch.getId(), UUID.randomUUID()))
                                        .isInstanceOf(ValidationException.class);
                }
        }

        @Nested
        class Release {

                @Test
                void ranksAllDifficulties_andMarksBatchAsReleased() {
                        Batch batch = buildBatch(BatchStatus.RELEASE_READY);
                        MapDifficulty diff1 = buildDifficulty(batch);
                        MapDifficulty diff2 = buildDifficulty(batch);
                        when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
                        when(mapDifficultyRepository.findByBatch_IdAndActiveTrue(batch.getId()))
                                        .thenReturn(List.of(diff1, diff2));
                        when(mapDifficultyRepository.saveAll(any())).thenReturn(List.of(diff1, diff2));
                        when(batchRepository.save(any())).thenReturn(batch);
                        when(complexityService.findActiveComplexitiesForDifficulties(any()))
                                        .thenReturn(java.util.Map.of(
                                                        diff1.getId(), java.math.BigDecimal.valueOf(8.0),
                                                        diff2.getId(), java.math.BigDecimal.valueOf(9.0)));
                        when(statisticsService.findActiveForDifficulties(any()))
                                        .thenReturn(java.util.Map.of());

                        batchService.release(batch.getId());

                        assertThat(diff1.getStatus()).isEqualTo(MapDifficultyStatus.RANKED);
                        assertThat(diff1.getRankedAt()).isNotNull();
                        assertThat(diff2.getStatus()).isEqualTo(MapDifficultyStatus.RANKED);
                        assertThat(diff2.getRankedAt()).isNotNull();
                        assertThat(batch.getStatus()).isEqualTo(BatchStatus.RELEASED);
                        assertThat(batch.getReleasedAt()).isNotNull();
                }

                @Test
                void allDifficulties_getTheSameRankedAtTimestamp() {
                        Batch batch = buildBatch(BatchStatus.RELEASE_READY);
                        MapDifficulty diff1 = buildDifficulty(batch);
                        MapDifficulty diff2 = buildDifficulty(batch);
                        when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
                        when(mapDifficultyRepository.findByBatch_IdAndActiveTrue(batch.getId()))
                                        .thenReturn(List.of(diff1, diff2));
                        when(mapDifficultyRepository.saveAll(any())).thenReturn(List.of(diff1, diff2));
                        when(batchRepository.save(any())).thenReturn(batch);
                        when(complexityService.findActiveComplexitiesForDifficulties(any()))
                                        .thenReturn(java.util.Map.of(
                                                        diff1.getId(), java.math.BigDecimal.valueOf(8.0),
                                                        diff2.getId(), java.math.BigDecimal.valueOf(9.0)));
                        when(statisticsService.findActiveForDifficulties(any()))
                                        .thenReturn(java.util.Map.of());

                        batchService.release(batch.getId());

                        assertThat(diff1.getRankedAt()).isEqualTo(diff2.getRankedAt());
                }

                @Test
                void throwsConflict_whenBatchIsAlreadyReleased() {
                        Batch batch = buildBatch(BatchStatus.RELEASED);
                        when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));

                        assertThatThrownBy(() -> batchService.release(batch.getId()))
                                        .isInstanceOf(ConflictException.class)
                                        .hasMessageContaining("already released");
                }

                @Test
                void throwsNotFound_whenBatchDoesNotExist() {
                        UUID id = UUID.randomUUID();
                        when(batchRepository.findById(id)).thenReturn(Optional.empty());

                        assertThatThrownBy(() -> batchService.release(id))
                                        .isInstanceOf(ResourceNotFoundException.class);
                }

                @Test
                void releaseWithNoDifficulties_stillReleasesTheBatch() {
                        Batch batch = buildBatch(BatchStatus.DRAFT);
                        when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
                        when(mapDifficultyRepository.findByBatch_IdAndActiveTrue(batch.getId()))
                                        .thenReturn(List.of());
                        when(mapDifficultyRepository.saveAll(any())).thenReturn(List.of());
                        when(batchRepository.save(any())).thenReturn(batch);

                        batchService.release(batch.getId());

                        assertThat(batch.getStatus()).isEqualTo(BatchStatus.RELEASED);
                        assertThat(batch.getReleasedAt()).isNotNull();
                }
        }

        @Nested
        class ReweightBatch {

                private final Long staffUserId = 12345L;
                private final UUID staffId = UUID.randomUUID();

                @Test
                void updatesComplexitiesAndTriggersRecalculateBatchAsync() {
                        Batch batch = buildBatch(BatchStatus.RELEASED);
                        MapDifficulty diff1 = buildDifficulty(batch);
                        MapDifficulty diff2 = buildDifficulty(batch);

                        ApproveReweightRequest item1 = new ApproveReweightRequest();
                        item1.setMapDifficultyId(diff1.getId());
                        item1.setComplexity(java.math.BigDecimal.valueOf(8.5));
                        item1.setReason("reweight reason");

                        ApproveReweightRequest item2 = new ApproveReweightRequest();
                        item2.setMapDifficultyId(diff2.getId());
                        item2.setComplexity(java.math.BigDecimal.valueOf(9.0));
                        item2.setReason("reweight reason 2");

                        List<ApproveReweightRequest> items = List.of(item1, item2);

                        when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
                        when(mapDifficultyRepository.findByBatchIdAndActiveTrueWithCategory(batch.getId()))
                                        .thenReturn(List.of(diff1, diff2));
                        when(complexityService.findActiveComplexitiesForDifficulties(any()))
                                        .thenReturn(java.util.Map.of());
                        when(statisticsService.findActiveForDifficulties(any()))
                                        .thenReturn(java.util.Map.of());

                        batchService.reweightBatch(batch.getId(), items, staffUserId, staffId);

                        verify(mapService).updateComplexity(eq(diff1.getId()), any(UpdateMapComplexityRequest.class),
                                        eq(staffUserId), eq(staffId));
                        verify(mapService).updateComplexity(eq(diff2.getId()), any(UpdateMapComplexityRequest.class),
                                        eq(staffUserId), eq(staffId));
                        verify(scoreRecalculationService).recalculateBatchAsync(List.of(diff1, diff2));
                }

                @Test
                void throwsNotFound_whenBatchDoesNotExist() {
                        UUID id = UUID.randomUUID();
                        when(batchRepository.findById(id)).thenReturn(Optional.empty());

                        assertThatThrownBy(() -> batchService.reweightBatch(id, List.of(), staffUserId, staffId))
                                        .isInstanceOf(ResourceNotFoundException.class);
                }

                @Test
                void throwsValidation_whenBatchIsNotReleased() {
                        Batch batch = buildBatch(BatchStatus.DRAFT);
                        when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));

                        assertThatThrownBy(() -> batchService.reweightBatch(
                                        batch.getId(), List.of(), staffUserId, staffId))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("released");
                }

                @Test
                void throwsValidation_whenBatchIsReleaseReady() {
                        Batch batch = buildBatch(BatchStatus.RELEASE_READY);
                        when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));

                        assertThatThrownBy(() -> batchService.reweightBatch(
                                        batch.getId(), List.of(), staffUserId, staffId))
                                        .isInstanceOf(ValidationException.class);
                }

                @Test
                void throwsValidation_whenBatchHasNoDifficulties() {
                        Batch batch = buildBatch(BatchStatus.RELEASED);
                        when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
                        when(mapDifficultyRepository.findByBatchIdAndActiveTrueWithCategory(batch.getId()))
                                        .thenReturn(List.of());

                        ApproveReweightRequest item = new ApproveReweightRequest();
                        item.setMapDifficultyId(UUID.randomUUID());
                        item.setComplexity(java.math.BigDecimal.valueOf(8.0));

                        assertThatThrownBy(() -> batchService.reweightBatch(
                                        batch.getId(), List.of(item), staffUserId, staffId))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("no active difficulties");
                }

                @Test
                void throwsValidation_whenDifficultyNotInBatch() {
                        Batch batch = buildBatch(BatchStatus.RELEASED);
                        MapDifficulty diff = buildDifficulty(batch);

                        when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
                        when(mapDifficultyRepository.findByBatchIdAndActiveTrueWithCategory(batch.getId()))
                                        .thenReturn(List.of(diff));

                        ApproveReweightRequest item = new ApproveReweightRequest();
                        item.setMapDifficultyId(UUID.randomUUID());
                        item.setComplexity(java.math.BigDecimal.valueOf(8.0));

                        assertThatThrownBy(() -> batchService.reweightBatch(
                                        batch.getId(), List.of(item), staffUserId, staffId))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("not in this batch");
                }
        }

        private Batch buildBatch(BatchStatus status) {
                return Batch.builder()
                                .id(UUID.randomUUID())
                                .name("Test Batch")
                                .description("A batch for testing")
                                .status(status)
                                .build();
        }

        private MapDifficulty buildDifficulty(Batch batch) {
                return MapDifficulty.builder()
                                .id(UUID.randomUUID())
                                .map(buildMap())
                                .category(buildCategory())
                                .difficulty(Difficulty.EXPERT_PLUS)
                                .characteristic("Standard")
                                .status(MapDifficultyStatus.QUALIFIED)
                                .maxScore(1_000_000)
                                .batch(batch)
                                .active(true)
                                .build();
        }

        private MapDifficulty buildStandaloneDifficulty() {
                return MapDifficulty.builder()
                                .id(UUID.randomUUID())
                                .map(buildMap())
                                .category(buildCategory())
                                .difficulty(Difficulty.EXPERT_PLUS)
                                .characteristic("Standard")
                                .status(MapDifficultyStatus.QUALIFIED)
                                .maxScore(1_000_000)
                                .active(true)
                                .build();
        }

        private Map buildMap() {
                return Map.builder()
                                .id(UUID.randomUUID())
                                .songName("Song")
                                .songAuthor("Author")
                                .songHash("hash123")
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
}
