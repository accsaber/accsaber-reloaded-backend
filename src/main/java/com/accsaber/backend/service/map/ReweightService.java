package com.accsaber.backend.service.map;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
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

    @Transactional
    public MapDifficultyResponse reweight(UUID mapDifficultyId, BigDecimal complexity, String reason,
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

        scoreRecalculationService.recalculateDifficultyAsync(mapDifficultyId);
        mapService.evictRankedDifficultiesCache();

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
}
