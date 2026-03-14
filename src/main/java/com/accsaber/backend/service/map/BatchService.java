package com.accsaber.backend.service.map;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.map.CreateBatchRequest;
import com.accsaber.backend.model.dto.request.map.UpdateBatchStatusRequest;
import com.accsaber.backend.model.dto.response.map.BatchResponse;
import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.model.dto.response.map.MapDifficultyStatisticsResponse;
import com.accsaber.backend.model.entity.map.Batch;
import com.accsaber.backend.model.entity.map.BatchStatus;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.repository.map.BatchRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.staff.StaffUserRepository;
import com.accsaber.backend.service.score.ScoreImportService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BatchService {

    private final BatchRepository batchRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final StaffUserRepository staffUserRepository;
    private final MapDifficultyComplexityService complexityService;
    private final MapDifficultyStatisticsService statisticsService;
    private final ScoreImportService scoreImportService;

    public Page<BatchResponse> findAll(Pageable pageable) {
        Page<Batch> batches = batchRepository.findAll(pageable);
        return batches.map(b -> toResponse(b, loadDifficulties(b.getId())));
    }

    public Page<BatchResponse> findByStatus(BatchStatus status, Pageable pageable) {
        return batchRepository.findByStatus(status, pageable)
                .map(b -> toResponse(b, loadDifficulties(b.getId())));
    }

    public BatchResponse findById(UUID id) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", id));
        return toResponse(batch, loadDifficulties(id));
    }

    @Transactional
    public BatchResponse create(CreateBatchRequest request, UUID createdById) {
        StaffUser createdBy = staffUserRepository.findById(createdById)
                .orElseThrow(() -> new ResourceNotFoundException("StaffUser", createdById));
        Batch batch = batchRepository.save(Batch.builder()
                .name(request.getName())
                .description(request.getDescription())
                .createdBy(createdBy)
                .build());
        return toResponse(batch, List.of());
    }

    @Transactional
    public BatchResponse updateStatus(UUID id, UpdateBatchStatusRequest request) {
        Batch batch = findUnreleasedBatch(id);

        if (request.getStatus() == BatchStatus.RELEASED) {
            throw new ValidationException("Use the release endpoint to release a batch");
        }

        batch.setStatus(request.getStatus());
        batchRepository.save(batch);
        return toResponse(batch, loadDifficulties(id));
    }

    @Transactional
    public BatchResponse addDifficulty(UUID batchId, UUID difficultyId) {
        Batch batch = findUnreleasedBatch(batchId);
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrue(difficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", difficultyId));

        if (difficulty.getBatch() != null && !difficulty.getBatch().getId().equals(batchId)) {
            throw new ConflictException("Difficulty is already assigned to another batch");
        }

        difficulty.setBatch(batch);
        mapDifficultyRepository.save(difficulty);
        return toResponse(batch, loadDifficulties(batchId));
    }

    @Transactional
    public BatchResponse removeDifficulty(UUID batchId, UUID difficultyId) {
        findUnreleasedBatch(batchId);
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrue(difficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", difficultyId));

        difficulty.setBatch(null);
        mapDifficultyRepository.save(difficulty);
        return toResponse(batchRepository.findById(batchId).orElseThrow(), loadDifficulties(batchId));
    }

    @Transactional
    public BatchResponse release(UUID batchId) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", batchId));

        if (batch.getStatus() == BatchStatus.RELEASED) {
            throw new ConflictException("Batch is already released");
        }

        Instant releasedAt = Instant.now();

        List<MapDifficulty> difficulties = mapDifficultyRepository.findByBatch_IdAndActiveTrue(batchId);
        difficulties.forEach(d -> {
            d.setStatus(MapDifficultyStatus.RANKED);
            d.setRankedAt(releasedAt);
        });
        mapDifficultyRepository.saveAll(difficulties);

        batch.setStatus(BatchStatus.RELEASED);
        batch.setReleasedAt(releasedAt);
        batchRepository.save(batch);

        scoreImportService.backfillDifficultiesSequentiallyAsync(
                difficulties.stream().map(MapDifficulty::getId).toList());

        return toResponse(batch, enrich(difficulties));
    }

    private Batch findUnreleasedBatch(UUID batchId) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", batchId));
        if (batch.getStatus() == BatchStatus.RELEASED) {
            throw new ValidationException("Cannot modify a released batch");
        }
        return batch;
    }

    private List<MapDifficultyResponse> loadDifficulties(UUID batchId) {
        return enrich(mapDifficultyRepository.findByBatch_IdAndActiveTrue(batchId));
    }

    private List<MapDifficultyResponse> enrich(List<MapDifficulty> difficulties) {
        if (difficulties.isEmpty())
            return List.of();

        List<UUID> ids = difficulties.stream().map(MapDifficulty::getId).toList();
        Map<UUID, BigDecimal> complexities = complexityService.findActiveComplexitiesForDifficulties(ids);
        Map<UUID, MapDifficultyStatisticsResponse> stats = statisticsService.findActiveForDifficulties(ids);

        return difficulties.stream()
                .map(d -> toDifficultyResponse(d, complexities.get(d.getId()), stats.get(d.getId())))
                .toList();
    }

    private BatchResponse toResponse(Batch batch, List<MapDifficultyResponse> difficulties) {
        return BatchResponse.builder()
                .id(batch.getId())
                .name(batch.getName())
                .description(batch.getDescription())
                .status(batch.getStatus())
                .difficulties(difficulties)
                .releasedAt(batch.getReleasedAt())
                .createdAt(batch.getCreatedAt())
                .updatedAt(batch.getUpdatedAt())
                .build();
    }

    private MapDifficultyResponse toDifficultyResponse(MapDifficulty d, BigDecimal complexity,
            MapDifficultyStatisticsResponse stats) {
        return MapDifficultyResponse.builder()
                .id(d.getId())
                .mapId(d.getMap().getId())
                .categoryId(d.getCategory().getId())
                .previousVersionId(d.getPreviousVersion() != null ? d.getPreviousVersion().getId() : null)
                .difficulty(d.getDifficulty())
                .characteristic(d.getCharacteristic())
                .status(d.getStatus())
                .ssLeaderboardId(d.getSsLeaderboardId())
                .blLeaderboardId(d.getBlLeaderboardId())
                .maxScore(d.getMaxScore())
                .complexity(complexity)
                .rankedAt(d.getRankedAt())
                .statistics(stats)
                .build();
    }
}
