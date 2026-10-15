package com.accsaber.backend.service.map;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.map.CreateBatchRequest;
import com.accsaber.backend.model.dto.request.map.UpdateBatchRequest;
import com.accsaber.backend.model.dto.request.map.UpdateBatchStatusRequest;
import com.accsaber.backend.model.dto.response.map.BatchResponse;
import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.model.dto.response.map.MapDifficultyStatisticsResponse;
import com.accsaber.backend.model.dto.response.map.PublicMapDifficultyResponse;
import com.accsaber.backend.model.entity.map.Batch;
import com.accsaber.backend.model.entity.map.BatchStatus;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.repository.map.BatchRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.staff.StaffUserRepository;
import com.accsaber.backend.service.playlist.PlaylistService;
import com.accsaber.backend.service.score.CampaignScoreGate;
import com.accsaber.backend.service.score.ScoreImportService;
import com.accsaber.backend.service.score.ScoreIngestionService;
import com.accsaber.backend.util.Texts;

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
    private final ScoreIngestionService scoreIngestionService;
    private final CampaignScoreGate campaignScoreGate;
    private final MapService mapService;
    private final PlaylistService playlistService;

    public Page<BatchResponse<MapDifficultyResponse>> findAll(BatchStatus status, String search, Pageable pageable) {
        return toResponsesWithDifficulties(
                batchRepository.findFiltered(status, Texts.trimToNull(search), resolveBatchSort(pageable)));
    }

    public BatchResponse<MapDifficultyResponse> findById(UUID id) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", id));
        return toResponse(batch, loadDifficulties(id));
    }

    public Page<BatchResponse<PublicMapDifficultyResponse>> findAllPublic(String search, Pageable pageable) {
        return findAll(BatchStatus.RELEASED, search, pageable).map(BatchService::toPublicResponse);
    }

    public BatchResponse<PublicMapDifficultyResponse> findByIdPublic(UUID id) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", id));
        if (batch.getStatus() != BatchStatus.RELEASED) {
            throw new ResourceNotFoundException("Batch", id);
        }
        return toPublicResponse(toResponse(batch, loadDifficulties(id)));
    }

    private static BatchResponse<PublicMapDifficultyResponse> toPublicResponse(
            BatchResponse<MapDifficultyResponse> batch) {
        return BatchResponse.<PublicMapDifficultyResponse>builder()
                .id(batch.getId())
                .name(batch.getName())
                .description(batch.getDescription())
                .status(batch.getStatus())
                .difficulties(batch.getDifficulties().stream()
                        .map(MapService::toPublicDifficultyResponse)
                        .toList())
                .releasedAt(batch.getReleasedAt())
                .createdAt(batch.getCreatedAt())
                .updatedAt(batch.getUpdatedAt())
                .build();
    }

    @Transactional
    public BatchResponse<MapDifficultyResponse> create(CreateBatchRequest request, UUID createdById) {
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
    public BatchResponse<MapDifficultyResponse> update(UUID id, UpdateBatchRequest request) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", id));

        batch.setName(request.getName());
        batch.setDescription(request.getDescription());
        batchRepository.save(batch);
        return toResponse(batch, loadDifficulties(id));
    }

    @Transactional
    public BatchResponse<MapDifficultyResponse> updateStatus(UUID id, UpdateBatchStatusRequest request) {
        Batch batch = findUnreleasedBatch(id);

        if (request.getStatus() == BatchStatus.RELEASED) {
            throw new ValidationException("Use the release endpoint to release a batch");
        }

        batch.setStatus(request.getStatus());
        batchRepository.save(batch);
        return toResponse(batch, loadDifficulties(id));
    }

    @Transactional
    public BatchResponse<MapDifficultyResponse> addDifficulty(UUID batchId, UUID difficultyId) {
        Batch batch = findUnreleasedBatch(batchId);
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrue(difficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", difficultyId));

        if (difficulty.getBatch() != null && !difficulty.getBatch().getId().equals(batchId)) {
            throw new ConflictException("Difficulty is already assigned to another batch");
        }
        if (difficulty.getStatus() == MapDifficultyStatus.CAMPAIGN) {
            throw new ValidationException(
                    "Campaign-imported difficulties must be promoted to queue before batching");
        }

        difficulty.setBatch(batch);
        mapDifficultyRepository.save(difficulty);
        return toResponse(batch, loadDifficulties(batchId));
    }

    @Transactional
    public BatchResponse<MapDifficultyResponse> removeDifficulty(UUID batchId, UUID difficultyId) {
        findUnreleasedBatch(batchId);
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrue(difficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", difficultyId));

        difficulty.setBatch(null);
        mapDifficultyRepository.save(difficulty);
        return toResponse(batchRepository.findById(batchId).orElseThrow(), loadDifficulties(batchId));
    }

    @Transactional
    public BatchResponse<MapDifficultyResponse> release(UUID batchId) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", batchId));

        if (batch.getStatus() == BatchStatus.RELEASED) {
            throw new ConflictException("Batch is already released");
        }

        Instant releasedAt = Instant.now();

        List<MapDifficulty> difficulties = mapDifficultyRepository.findByBatch_IdAndActiveTrue(batchId);
        List<UUID> difficultyIds = difficulties.stream().map(MapDifficulty::getId).toList();
        var complexities = complexityService.findActiveComplexitiesForDifficulties(difficultyIds);
        List<UUID> missingComplexity = difficultyIds.stream()
                .filter(id -> !complexities.containsKey(id))
                .toList();
        if (!missingComplexity.isEmpty()) {
            throw new ValidationException(
                    "Cannot release batch: difficulties missing complexity: " + missingComplexity);
        }
        difficulties.forEach(d -> {
            d.setStatus(MapDifficultyStatus.RANKED);
            d.setRankedAt(releasedAt);
        });
        mapDifficultyRepository.saveAll(difficulties);
        scoreIngestionService.refreshRankedLeaderboardIds();
        campaignScoreGate.refresh();

        batch.setStatus(BatchStatus.RELEASED);
        batch.setReleasedAt(releasedAt);
        batchRepository.save(batch);

        scoreImportService.backfillDifficultiesAsync(
                difficulties.stream().map(MapDifficulty::getId).toList());

        playlistService.evictAllPlaylists();
        playlistService.evictAllUnrankedPlaylists();
        mapService.evictRankedDifficultiesCache();

        return toResponse(batch, enrich(difficulties));
    }

    private static Pageable resolveBatchSort(Pageable pageable) {
        if (!pageable.getSort().isSorted()) {
            return pageable;
        }
        Sort resolved = Sort.unsorted();
        for (Sort.Order order : pageable.getSort()) {
            if ("difficultyCount".equals(order.getProperty())) {
                resolved = resolved.and(JpaSort.unsafe(order.getDirection(), "COUNT(d)"));
            } else {
                resolved = resolved.and(Sort.by(
                        new Sort.Order(order.getDirection(), order.getProperty(),
                                Sort.NullHandling.NULLS_LAST)));
            }
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), resolved);
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

    private Page<BatchResponse<MapDifficultyResponse>> toResponsesWithDifficulties(Page<Batch> batches) {
        List<UUID> batchIds = batches.getContent().stream().map(Batch::getId).toList();
        List<MapDifficulty> difficulties = batchIds.isEmpty()
                ? List.of()
                : mapDifficultyRepository.findByBatch_IdInAndActiveTrue(batchIds);
        List<MapDifficultyResponse> responses = enrich(difficulties);
        Map<UUID, List<MapDifficultyResponse>> byBatch = new HashMap<>();
        for (int i = 0; i < difficulties.size(); i++) {
            byBatch.computeIfAbsent(difficulties.get(i).getBatch().getId(), k -> new ArrayList<>())
                    .add(responses.get(i));
        }
        return batches.map(b -> toResponse(b, byBatch.getOrDefault(b.getId(), List.of())));
    }

    private List<MapDifficultyResponse> enrich(List<MapDifficulty> difficulties) {
        if (difficulties.isEmpty())
            return List.of();

        List<UUID> ids = difficulties.stream().map(MapDifficulty::getId).toList();
        Map<UUID, Double> complexities = complexityService.findActiveComplexitiesForDifficulties(ids);
        Map<UUID, MapDifficultyStatisticsResponse> stats = statisticsService.findActiveForDifficulties(ids);

        return difficulties.stream()
                .map(d -> toDifficultyResponse(d, complexities.get(d.getId()), stats.get(d.getId())))
                .toList();
    }

    private BatchResponse<MapDifficultyResponse> toResponse(Batch batch, List<MapDifficultyResponse> difficulties) {
        return BatchResponse.<MapDifficultyResponse>builder()
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

    private static MapDifficultyResponse toDifficultyResponse(MapDifficulty d, Double complexity,
            MapDifficultyStatisticsResponse stats) {
        return MapService.toDifficultyResponse(d, MapService.DifficultyContext.basic(complexity, stats, null));
    }
}
