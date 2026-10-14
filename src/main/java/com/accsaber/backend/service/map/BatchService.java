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
import com.accsaber.backend.model.dto.response.map.PublicBatchResponse;
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

    public Page<BatchResponse> findAll(BatchStatus status, String search, Pageable pageable) {
        String term = search == null || search.isBlank() ? null : search.trim();
        return toResponsesWithDifficulties(batchRepository.findFiltered(status, term, resolveBatchSort(pageable)));
    }

    public BatchResponse findById(UUID id) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", id));
        return toResponse(batch, loadDifficulties(id));
    }

    public Page<PublicBatchResponse> findAllPublic(String search, Pageable pageable) {
        return findAll(BatchStatus.RELEASED, search, pageable).map(BatchService::toPublicResponse);
    }

    public PublicBatchResponse findByIdPublic(UUID id) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", id));
        if (batch.getStatus() != BatchStatus.RELEASED) {
            throw new ResourceNotFoundException("Batch", id);
        }
        return toPublicResponse(toResponse(batch, loadDifficulties(id)));
    }

    private static PublicBatchResponse toPublicResponse(BatchResponse batch) {
        return PublicBatchResponse.builder()
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
    public BatchResponse update(UUID id, UpdateBatchRequest request) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", id));

        batch.setName(request.getName());
        batch.setDescription(request.getDescription());
        batchRepository.save(batch);
        return toResponse(batch, loadDifficulties(id));
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
        if (difficulty.getStatus() == MapDifficultyStatus.CAMPAIGN) {
            throw new ValidationException(
                    "Campaign-imported difficulties must be promoted to queue before batching");
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
                resolved = resolved
                        .and(JpaSort.unsafe(Sort.Direction.ASC, "(CASE WHEN COUNT(d) IS NULL THEN 1 ELSE 0 END)"))
                        .and(JpaSort.unsafe(order.getDirection(), "COUNT(d)"));
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

    private Page<BatchResponse> toResponsesWithDifficulties(Page<Batch> batches) {
        Map<UUID, List<MapDifficulty>> byBatch = new HashMap<>();
        List<UUID> allDifficultyIds = new ArrayList<>();
        for (Batch b : batches.getContent()) {
            List<MapDifficulty> diffs = mapDifficultyRepository.findByBatch_IdAndActiveTrue(b.getId());
            byBatch.put(b.getId(), diffs);
            for (MapDifficulty d : diffs) {
                allDifficultyIds.add(d.getId());
            }
        }
        Map<UUID, Double> complexities = allDifficultyIds.isEmpty()
                ? Map.of()
                : complexityService.findActiveComplexitiesForDifficulties(allDifficultyIds);
        Map<UUID, MapDifficultyStatisticsResponse> stats = allDifficultyIds.isEmpty()
                ? Map.of()
                : statisticsService.findActiveForDifficulties(allDifficultyIds);
        return batches.map(b -> toResponse(b, byBatch.get(b.getId()).stream()
                .map(d -> toDifficultyResponse(d, complexities.get(d.getId()), stats.get(d.getId())))
                .toList()));
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

    private static MapDifficultyResponse toDifficultyResponse(MapDifficulty d, Double complexity,
            MapDifficultyStatisticsResponse stats) {
        return MapService.toDifficultyResponse(d, MapService.DifficultyContext.basic(complexity, stats, null));
    }
}
