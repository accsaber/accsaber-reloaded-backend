package com.accsaber.backend.service.map;

import java.math.BigDecimal;
import java.time.Instant;
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
import com.accsaber.backend.model.dto.request.map.ApproveReweightRequest;
import com.accsaber.backend.model.dto.request.map.CreateBatchRequest;
import com.accsaber.backend.model.dto.request.map.UpdateBatchStatusRequest;
import com.accsaber.backend.model.dto.request.map.UpdateMapComplexityRequest;
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
import com.accsaber.backend.service.score.ScoreImportService;
import com.accsaber.backend.service.score.ScoreIngestionService;
import com.accsaber.backend.service.score.ScoreRecalculationService;

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
    private final ScoreRecalculationService scoreRecalculationService;
    private final MapService mapService;
    private final PlaylistService playlistService;

    public Page<BatchResponse> findAll(String search, Pageable pageable) {
        Pageable effective = resolveBatchSort(pageable);
        boolean hasSearch = search != null && !search.isBlank();
        boolean needsCountSort = hasDifficultyCountSort(pageable);
        Page<Batch> batches;
        if (needsCountSort && hasSearch) {
            batches = batchRepository.findAllWithDifficultyCountAndSearch(search.trim(), effective);
        } else if (needsCountSort) {
            batches = batchRepository.findAllWithDifficultyCount(effective);
        } else if (hasSearch) {
            batches = batchRepository.findAllWithSearch(search.trim(), effective);
        } else {
            batches = batchRepository.findAll(effective);
        }
        return batches.map(b -> toResponse(b, loadDifficulties(b.getId())));
    }

    public Page<BatchResponse> findByStatus(BatchStatus status, String search, Pageable pageable) {
        Pageable effective = resolveBatchSort(pageable);
        boolean hasSearch = search != null && !search.isBlank();
        boolean needsCountSort = hasDifficultyCountSort(pageable);
        Page<Batch> batches;
        if (needsCountSort && hasSearch) {
            batches = batchRepository.findByStatusWithDifficultyCountAndSearch(status, search.trim(), effective);
        } else if (needsCountSort) {
            batches = batchRepository.findByStatusWithDifficultyCount(status, effective);
        } else if (hasSearch) {
            batches = batchRepository.findByStatusWithSearch(status, search.trim(), effective);
        } else {
            batches = batchRepository.findByStatus(status, effective);
        }
        return batches.map(b -> toResponse(b, loadDifficulties(b.getId())));
    }

    public BatchResponse findById(UUID id) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", id));
        return toResponse(batch, loadDifficulties(id));
    }

    public Page<PublicBatchResponse> findAllPublic(String search, Pageable pageable) {
        return findAll(search, pageable).map(BatchService::toPublicResponse);
    }

    public Page<PublicBatchResponse> findByStatusPublic(BatchStatus status, String search, Pageable pageable) {
        return findByStatus(status, search, pageable).map(BatchService::toPublicResponse);
    }

    public PublicBatchResponse findByIdPublic(UUID id) {
        return toPublicResponse(findById(id));
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

    @Transactional
    public List<MapDifficultyResponse> reweightBatch(UUID batchId, List<ApproveReweightRequest> items,
            Long staffUserId, UUID staffId) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", batchId));

        if (batch.getStatus() != BatchStatus.RELEASED) {
            throw new ValidationException("Can only reweight a released batch");
        }

        List<MapDifficulty> batchDifficulties = mapDifficultyRepository
                .findByBatchIdAndActiveTrueWithCategory(batchId);

        if (batchDifficulties.isEmpty()) {
            throw new ValidationException("Batch has no active difficulties to reweight");
        }

        List<UUID> batchDifficultyIds = batchDifficulties.stream().map(MapDifficulty::getId).toList();
        List<UUID> requestedIds = items.stream().map(ApproveReweightRequest::getMapDifficultyId).toList();
        List<UUID> invalid = requestedIds.stream()
                .filter(id -> !batchDifficultyIds.contains(id))
                .toList();
        if (!invalid.isEmpty()) {
            throw new ValidationException("Difficulties not in this batch: " + invalid);
        }

        for (ApproveReweightRequest item : items) {
            UpdateMapComplexityRequest req = new UpdateMapComplexityRequest();
            req.setComplexity(item.getComplexity());
            req.setReason(item.getReason());
            mapService.updateComplexity(item.getMapDifficultyId(), req, staffUserId, staffId);
        }

        List<MapDifficulty> affectedDifficulties = batchDifficulties.stream()
                .filter(d -> requestedIds.contains(d.getId()))
                .toList();
        scoreRecalculationService.recalculateBatchAsync(affectedDifficulties);
        mapService.evictRankedDifficultiesCache();

        return enrich(affectedDifficulties);
    }

    private static boolean hasDifficultyCountSort(Pageable pageable) {
        return pageable.getSort().stream()
                .anyMatch(order -> "difficultyCount".equals(order.getProperty()));
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
        com.accsaber.backend.model.entity.map.Map map = d.getMap();
        return MapDifficultyResponse.builder()
                .id(d.getId())
                .mapId(map.getId())
                .songName(map.getSongName())
                .songAuthor(map.getSongAuthor())
                .mapAuthor(map.getMapAuthor())
                .coverUrl(map.getCoverUrl())
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
                .createdAt(d.getCreatedAt())
                .createdBy(d.getCreatedBy())
                .statistics(stats)
                .build();
    }
}
