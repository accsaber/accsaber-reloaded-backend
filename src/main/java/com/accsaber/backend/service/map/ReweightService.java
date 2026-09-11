package com.accsaber.backend.service.map;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.map.ApproveReweightRequest;
import com.accsaber.backend.model.dto.request.map.BulkReweightRequest;
import com.accsaber.backend.model.dto.request.map.UpdateMapComplexityRequest;
import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.model.entity.map.Batch;
import com.accsaber.backend.model.entity.map.BatchStatus;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.map.BatchRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.service.score.ScoreRecalculationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReweightService {

    private final MapDifficultyRepository mapDifficultyRepository;
    private final MapService mapService;
    private final ScoreRecalculationService scoreRecalculationService;
    private final BatchRepository batchRepository;
    private final ComplexityScenarioService scenarioService;

    @Transactional
    public MapDifficultyResponse setComplexityByHand(UUID mapDifficultyId, Double complexity, String reason,
            Long staffUserId, UUID staffId) {
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrue(mapDifficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", mapDifficultyId));
        if (difficulty.getStatus() == MapDifficultyStatus.RANKED) {
            reweight(mapDifficultyId, complexity, reason, staffUserId, staffId);
        } else {
            UpdateMapComplexityRequest request = new UpdateMapComplexityRequest();
            request.setComplexity(complexity);
            request.setReason(reason);
            mapService.updateComplexity(mapDifficultyId, request, staffUserId, staffId);
        }
        return mapService.setComplexityPinned(mapDifficultyId, true, staffId);
    }

    @Transactional
    public MapDifficultyResponse reweight(UUID mapDifficultyId, Double complexity, String reason,
            Long staffUserId, UUID staffId) {
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrue(mapDifficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", mapDifficultyId));

        if (difficulty.getStatus() != MapDifficultyStatus.RANKED) {
            throw new ValidationException("Reweight is only allowed on RANKED difficulties");
        }

        UpdateMapComplexityRequest req = new UpdateMapComplexityRequest();
        req.setComplexity(complexity);
        req.setReason(reason);
        mapService.updateComplexity(mapDifficultyId, req, staffUserId, staffId);

        afterCommit(() -> {
            scoreRecalculationService.recalculateDifficultyAsync(mapDifficultyId);
            scenarioService.rebuildAsync();
        });
        mapService.evictRankedDifficultiesCache();
        scenarioService.evict();

        return mapService.getDifficultyResponse(mapDifficultyId);
    }

    public void recalculateDifficulty(UUID mapDifficultyId) {
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrue(mapDifficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", mapDifficultyId));

        if (difficulty.getStatus() != MapDifficultyStatus.RANKED) {
            throw new ValidationException("Recalculate is only allowed on RANKED difficulties");
        }

        scoreRecalculationService.recalculateDifficultyAsync(mapDifficultyId);
        mapService.evictRankedDifficultiesCache();
        log.info("Triggered recalculation for difficulty {}", mapDifficultyId);
    }

    public void recalculateBatch(UUID batchId) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", batchId));

        if (batch.getStatus() != BatchStatus.RELEASED) {
            throw new ValidationException("Can only recalculate a released batch");
        }

        List<MapDifficulty> difficulties = mapDifficultyRepository
                .findByBatchIdAndActiveTrueWithCategory(batchId);

        if (difficulties.isEmpty()) {
            throw new ValidationException("Batch has no active difficulties to recalculate");
        }

        scoreRecalculationService.recalculateBatchAsync(difficulties);
        mapService.evictRankedDifficultiesCache();
        log.info("Triggered batch recalculation for {} difficulties in batch {}", difficulties.size(), batchId);
    }

    @Transactional
    public void bulkReweight(List<BulkReweightRequest.Item> items, String reason,
            Long staffUserId, UUID staffId) {
        Map<UUID, Change> changes = new LinkedHashMap<>();
        for (BulkReweightRequest.Item item : items) {
            changes.put(item.getMapDifficultyId(), new Change(item.getComplexity(), reason));
        }
        applyChanges(changes, staffUserId, staffId);
    }

    @Transactional
    public List<MapDifficultyResponse> reweightBatch(UUID batchId, List<ApproveReweightRequest> items,
            Long staffUserId, UUID staffId) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", batchId));
        if (batch.getStatus() != BatchStatus.RELEASED) {
            throw new ValidationException("Can only reweight a released batch");
        }
        List<MapDifficulty> inBatch = mapDifficultyRepository.findByBatchIdAndActiveTrueWithCategory(batchId);
        if (inBatch.isEmpty()) {
            throw new ValidationException("Batch has no active difficulties to reweight");
        }
        Set<UUID> allowed = inBatch.stream().map(MapDifficulty::getId).collect(Collectors.toSet());
        List<UUID> outside = items.stream()
                .map(ApproveReweightRequest::getMapDifficultyId)
                .filter(id -> !allowed.contains(id))
                .toList();
        if (!outside.isEmpty()) {
            throw new ValidationException("Difficulties not in this batch: " + outside);
        }
        Map<UUID, Change> changes = new LinkedHashMap<>();
        for (ApproveReweightRequest item : items) {
            changes.put(item.getMapDifficultyId(), new Change(item.getComplexity(), item.getReason()));
        }
        applyChanges(changes, staffUserId, staffId);
        return inBatch.stream()
                .filter(d -> changes.containsKey(d.getId()))
                .map(d -> mapService.getDifficultyResponse(d.getId()))
                .toList();
    }

    private void applyChanges(Map<UUID, Change> changes, Long staffUserId, UUID staffId) {
        List<MapDifficulty> difficulties = mapDifficultyRepository
                .findAllByIdInAndActiveTrueWithCategory(List.copyOf(changes.keySet())).stream()
                .filter(d -> d.getStatus() == MapDifficultyStatus.RANKED)
                .toList();

        List<UUID> foundIds = difficulties.stream().map(MapDifficulty::getId).toList();
        List<UUID> missing = changes.keySet().stream()
                .filter(id -> !foundIds.contains(id))
                .toList();
        if (!missing.isEmpty()) {
            throw new ValidationException("Difficulties not found or not RANKED: " + missing);
        }

        for (MapDifficulty difficulty : difficulties) {
            Change change = changes.get(difficulty.getId());
            UpdateMapComplexityRequest req = new UpdateMapComplexityRequest();
            req.setComplexity(change.complexity());
            req.setReason(change.reason());
            mapService.updateComplexity(difficulty.getId(), req, staffUserId, staffId);
        }

        afterCommit(() -> {
            scoreRecalculationService.recalculateBatchAsync(difficulties);
            scenarioService.rebuildAsync();
        });
        mapService.evictRankedDifficultiesCache();
        scenarioService.evict();
        log.info("Triggered reweight for {} difficulties", difficulties.size());
    }

    private record Change(Double complexity, String reason) {
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
