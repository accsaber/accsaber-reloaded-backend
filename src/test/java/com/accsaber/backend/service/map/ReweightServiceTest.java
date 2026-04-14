package com.accsaber.backend.service.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.map.BulkReweightRequest;
import com.accsaber.backend.model.dto.request.map.UpdateMapComplexityRequest;
import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.model.entity.map.Batch;
import com.accsaber.backend.model.entity.map.BatchStatus;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.map.BatchRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.service.score.ScoreRecalculationService;

@ExtendWith(MockitoExtension.class)
class ReweightServiceTest {

    @Mock
    private MapDifficultyRepository mapDifficultyRepository;

    @Mock
    private MapService mapService;

    @Mock
    private ScoreRecalculationService scoreRecalculationService;

    @Mock
    private BatchRepository batchRepository;

    @InjectMocks
    private ReweightService reweightService;

    @Nested
    class Reweight {

        @Test
        void throwsNotFound_whenDifficultyDoesNotExist() {
            UUID diffId = UUID.randomUUID();
            when(mapDifficultyRepository.findByIdAndActiveTrue(diffId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reweightService.reweight(diffId, BigDecimal.valueOf(8.0), null, null, null))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void throwsValidation_whenDifficultyNotRanked() {
            MapDifficulty diff = buildDifficulty(MapDifficultyStatus.QUEUE);
            when(mapDifficultyRepository.findByIdAndActiveTrue(diff.getId())).thenReturn(Optional.of(diff));

            assertThatThrownBy(() -> reweightService.reweight(diff.getId(), BigDecimal.valueOf(8.0), null, null, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("RANKED");
        }

        @Test
        void updatesComplexityAndTriggersRecalculation() {
            MapDifficulty diff = buildDifficulty(MapDifficultyStatus.RANKED);
            MapDifficultyResponse expected = MapDifficultyResponse.builder()
                    .id(diff.getId()).status(MapDifficultyStatus.RANKED).build();

            when(mapDifficultyRepository.findByIdAndActiveTrue(diff.getId())).thenReturn(Optional.of(diff));
            when(mapService.updateComplexity(eq(diff.getId()), any(UpdateMapComplexityRequest.class), any(), any()))
                    .thenReturn(expected);
            when(mapService.getDifficultyResponse(diff.getId())).thenReturn(expected);

            MapDifficultyResponse result = reweightService.reweight(
                    diff.getId(), BigDecimal.valueOf(8.5), "Reweight", null, null);

            assertThat(result).isEqualTo(expected);
            verify(mapService).updateComplexity(eq(diff.getId()), any(), any(), any());
            verify(scoreRecalculationService).recalculateDifficultyAsync(diff.getId());
            verify(mapService).evictRankedDifficultiesCache();
        }
    }

    @Nested
    class RecalculateDifficulty {

        @Test
        void throwsNotFound_whenDifficultyDoesNotExist() {
            UUID diffId = UUID.randomUUID();
            when(mapDifficultyRepository.findByIdAndActiveTrue(diffId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reweightService.recalculateDifficulty(diffId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void throwsValidation_whenDifficultyNotRanked() {
            MapDifficulty diff = buildDifficulty(MapDifficultyStatus.QUEUE);
            when(mapDifficultyRepository.findByIdAndActiveTrue(diff.getId())).thenReturn(Optional.of(diff));

            assertThatThrownBy(() -> reweightService.recalculateDifficulty(diff.getId()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("RANKED");
        }

        @Test
        void triggersAsyncRecalculationAndEvictsCache() {
            MapDifficulty diff = buildDifficulty(MapDifficultyStatus.RANKED);
            when(mapDifficultyRepository.findByIdAndActiveTrue(diff.getId())).thenReturn(Optional.of(diff));

            reweightService.recalculateDifficulty(diff.getId());

            verify(scoreRecalculationService).recalculateDifficultyAsync(diff.getId());
            verify(mapService).evictRankedDifficultiesCache();
        }
    }

    @Nested
    class RecalculateBatch {

        @Test
        void throwsNotFound_whenBatchDoesNotExist() {
            UUID batchId = UUID.randomUUID();
            when(batchRepository.findById(batchId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reweightService.recalculateBatch(batchId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void throwsValidation_whenBatchNotReleased() {
            Batch batch = Batch.builder().id(UUID.randomUUID()).status(BatchStatus.DRAFT).build();
            when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));

            assertThatThrownBy(() -> reweightService.recalculateBatch(batch.getId()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("released");
        }

        @Test
        void throwsValidation_whenBatchHasNoDifficulties() {
            Batch batch = Batch.builder().id(UUID.randomUUID()).status(BatchStatus.RELEASED).build();
            when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
            when(mapDifficultyRepository.findByBatchIdAndActiveTrueWithCategory(batch.getId()))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> reweightService.recalculateBatch(batch.getId()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("no active difficulties");
        }

        @Test
        void triggersBatchRecalculationAndEvictsCache() {
            Batch batch = Batch.builder().id(UUID.randomUUID()).status(BatchStatus.RELEASED).build();
            MapDifficulty diff1 = buildDifficulty(MapDifficultyStatus.RANKED);
            MapDifficulty diff2 = buildDifficulty(MapDifficultyStatus.RANKED);
            List<MapDifficulty> difficulties = List.of(diff1, diff2);

            when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
            when(mapDifficultyRepository.findByBatchIdAndActiveTrueWithCategory(batch.getId()))
                    .thenReturn(difficulties);

            reweightService.recalculateBatch(batch.getId());

            verify(scoreRecalculationService).recalculateBatchAsync(difficulties);
            verify(mapService).evictRankedDifficultiesCache();
        }
    }

    @Nested
    class BulkReweight {

        @Test
        void throwsValidation_whenDifficultyNotFound() {
            UUID diffId = UUID.randomUUID();
            BulkReweightRequest.Item item = new BulkReweightRequest.Item();
            item.setMapDifficultyId(diffId);
            item.setComplexity(BigDecimal.valueOf(8.0));

            when(mapDifficultyRepository.findAllById(any())).thenReturn(List.of());

            assertThatThrownBy(() -> reweightService.bulkReweight(
                    List.of(item), "reason", null, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("not found or not RANKED");
        }

        @Test
        void throwsValidation_whenDifficultyNotRanked() {
            MapDifficulty diff = buildDifficulty(MapDifficultyStatus.QUEUE);
            BulkReweightRequest.Item item = new BulkReweightRequest.Item();
            item.setMapDifficultyId(diff.getId());
            item.setComplexity(BigDecimal.valueOf(8.0));

            when(mapDifficultyRepository.findAllById(any())).thenReturn(List.of(diff));

            assertThatThrownBy(() -> reweightService.bulkReweight(
                    List.of(item), "reason", null, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("not found or not RANKED");
        }

        @Test
        void updatesComplexitiesAndTriggersBatchRecalculation() {
            MapDifficulty diff1 = buildDifficulty(MapDifficultyStatus.RANKED);
            MapDifficulty diff2 = buildDifficulty(MapDifficultyStatus.RANKED);

            BulkReweightRequest.Item item1 = new BulkReweightRequest.Item();
            item1.setMapDifficultyId(diff1.getId());
            item1.setComplexity(BigDecimal.valueOf(8.0));
            BulkReweightRequest.Item item2 = new BulkReweightRequest.Item();
            item2.setMapDifficultyId(diff2.getId());
            item2.setComplexity(BigDecimal.valueOf(9.0));

            when(mapDifficultyRepository.findAllById(any())).thenReturn(List.of(diff1, diff2));

            reweightService.bulkReweight(List.of(item1, item2), "Bulk reweight", 1L, UUID.randomUUID());

            verify(mapService).updateComplexity(eq(diff1.getId()), any(), eq(1L), any());
            verify(mapService).updateComplexity(eq(diff2.getId()), any(), eq(1L), any());
            verify(scoreRecalculationService).recalculateBatchAsync(List.of(diff1, diff2));
            verify(mapService).evictRankedDifficultiesCache();
        }

        @Test
        void doesNotTriggerRecalculation_whenNoValidDifficulties() {
            UUID diffId = UUID.randomUUID();
            BulkReweightRequest.Item item = new BulkReweightRequest.Item();
            item.setMapDifficultyId(diffId);
            item.setComplexity(BigDecimal.valueOf(8.0));

            when(mapDifficultyRepository.findAllById(any())).thenReturn(List.of());

            assertThatThrownBy(() -> reweightService.bulkReweight(
                    List.of(item), "reason", null, null))
                    .isInstanceOf(ValidationException.class);

            verify(scoreRecalculationService, never()).recalculateBatchAsync(any());
        }
    }

    private MapDifficulty buildDifficulty(MapDifficultyStatus status) {
        return MapDifficulty.builder()
                .id(UUID.randomUUID())
                .difficulty(Difficulty.EXPERT_PLUS)
                .characteristic("Standard")
                .status(status)
                .active(true)
                .build();
    }
}
