package com.accsaber.backend.service.map;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.map.ApproveReweightRequest;
import com.accsaber.backend.model.dto.request.map.UpdateMapComplexityRequest;
import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.service.score.ScoreRecalculationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReweightService {

    private final MapDifficultyRepository mapDifficultyRepository;
    private final MapService mapService;
    private final ScoreRecalculationService scoreRecalculationService;

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

        scoreRecalculationService.recalculateScoresAsync(mapDifficultyId);

        return mapService.getDifficultyResponse(mapDifficultyId);
    }

    @Transactional
    public List<MapDifficultyResponse> reweightBatch(List<ApproveReweightRequest> items,
            Long staffUserId, UUID staffId) {
        return items.stream()
                .map(item -> reweight(item.getMapDifficultyId(), item.getComplexity(), item.getReason(),
                        staffUserId, staffId))
                .toList();
    }
}
