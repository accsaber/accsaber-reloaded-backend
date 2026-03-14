package com.accsaber.backend.service.map;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.map.ApproveUnrankRequest;
import com.accsaber.backend.model.dto.request.map.UpdateMapStatusRequest;
import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.map.MapDifficultyRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UnrankService {

    private final MapDifficultyRepository mapDifficultyRepository;
    private final MapService mapService;

    @Transactional
    public MapDifficultyResponse unrank(UUID mapDifficultyId, String reason, UUID staffId) {
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrue(mapDifficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", mapDifficultyId));

        if (difficulty.getStatus() != MapDifficultyStatus.RANKED) {
            throw new ValidationException("Unrank is only allowed on RANKED difficulties");
        }

        UpdateMapStatusRequest req = new UpdateMapStatusRequest();
        req.setStatus(MapDifficultyStatus.QUEUE);
        req.setReason(reason);
        return mapService.updateStatus(mapDifficultyId, req, staffId);
    }

    @Transactional
    public List<MapDifficultyResponse> unrankBatch(List<ApproveUnrankRequest> items, UUID staffId) {
        if (items == null || items.isEmpty())
            return List.of();
        return items.stream()
                .map(item -> unrank(item.getMapDifficultyId(), item.getReason(), staffId))
                .toList();
    }
}
