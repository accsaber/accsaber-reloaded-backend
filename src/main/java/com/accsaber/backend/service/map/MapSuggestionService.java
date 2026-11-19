package com.accsaber.backend.service.map;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.TooManyRequestsException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.map.ImportMapFromLeaderboardIdsRequest;
import com.accsaber.backend.model.dto.request.map.SuggestMapRequest;
import com.accsaber.backend.model.dto.request.map.UpdateMapStatusRequest;
import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.model.dto.response.map.PublicMapDifficultyResponse;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.map.MapDifficultyRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MapSuggestionService {

    static final int MAX_OPEN_SUGGESTIONS = 5;

    private final MapImportService mapImportService;
    private final MapService mapService;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final MapSubscriptionService subscriptionService;
    private final MapSuggestionRateLimitService rateLimitService;
    private final TransactionTemplate transactionTemplate;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PublicMapDifficultyResponse suggest(Long playerId, SuggestMapRequest request) {
        if (!rateLimitService.tryAcquire(playerId)) {
            throw new TooManyRequestsException("You are suggesting maps too quickly. Try again in a few minutes.");
        }
        return transactionTemplate.execute(status -> doSuggest(playerId, request));
    }

    private PublicMapDifficultyResponse doSuggest(Long playerId, SuggestMapRequest request) {
        if (mapDifficultyRepository.countByImportedByAndStatusAndActiveTrue(playerId,
                MapDifficultyStatus.SUGGESTED) >= MAX_OPEN_SUGGESTIONS) {
            throw new ValidationException("You already have " + MAX_OPEN_SUGGESTIONS
                    + " open suggestions. Wait for the ranking team to go through them first");
        }
        ImportMapFromLeaderboardIdsRequest importRequest = new ImportMapFromLeaderboardIdsRequest();
        importRequest.setBlLeaderboardId(request.blLeaderboardId().trim());
        importRequest.setSsLeaderboardId(request.ssLeaderboardId());
        importRequest.setCategoryId(request.categoryId());

        UUID difficultyId = mapImportService
                .importByLeaderboardIds(importRequest, null, MapDifficultyStatus.SUGGESTED).getId();
        MapDifficulty difficulty = mapDifficultyRepository.findById(difficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", difficultyId));
        difficulty.setImportedBy(playerId);
        mapDifficultyRepository.save(difficulty);
        subscriptionService.subscribe(difficultyId, playerId);
        return mapService.getDifficultyResponsePublic(difficultyId);
    }

    @Transactional
    public void withdraw(UUID difficultyId, Long playerId) {
        MapDifficulty difficulty = mapDifficultyRepository.findActiveForUpdate(difficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", difficultyId));
        if (!playerId.equals(difficulty.getImportedBy())) {
            throw new ForbiddenException("Only the player who suggested this map can withdraw it");
        }
        if (difficulty.getStatus() != MapDifficultyStatus.SUGGESTED) {
            throw new ValidationException("This map already left the suggestions and can no longer be withdrawn");
        }
        mapService.withdrawSuggestion(difficulty);
    }

    @Transactional
    public MapDifficultyResponse accept(UUID difficultyId, UUID staffId) {
        MapDifficulty difficulty = mapDifficultyRepository.findActiveForUpdate(difficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", difficultyId));
        if (difficulty.getStatus() != MapDifficultyStatus.SUGGESTED) {
            throw new ValidationException("Only suggested difficulties can be added to the queue");
        }
        UpdateMapStatusRequest request = new UpdateMapStatusRequest();
        request.setStatus(MapDifficultyStatus.QUEUE);
        return mapService.updateStatus(difficultyId, request, staffId);
    }
}
