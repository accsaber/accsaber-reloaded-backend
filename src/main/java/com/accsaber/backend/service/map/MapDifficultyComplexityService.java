package com.accsaber.backend.service.map;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.dto.response.map.MapComplexityHistoryResponse;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyComplexity;
import com.accsaber.backend.repository.map.MapDifficultyComplexityRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapDifficultyComplexityService {

    private final MapDifficultyComplexityRepository complexityRepository;

    public Optional<BigDecimal> findActiveComplexity(UUID mapDifficultyId) {
        return complexityRepository.findByMapDifficultyIdAndActiveTrue(mapDifficultyId)
                .map(MapDifficultyComplexity::getComplexity);
    }

    public Map<UUID, BigDecimal> findActiveComplexitiesForDifficulties(List<UUID> difficultyIds) {
        return complexityRepository.findActiveByMapDifficultyIdIn(difficultyIds).stream()
                .collect(Collectors.toMap(
                        c -> c.getMapDifficulty().getId(),
                        MapDifficultyComplexity::getComplexity));
    }

    public List<MapComplexityHistoryResponse> getHistoryForMap(UUID mapId) {
        return complexityRepository.findAllByMapIdOrderByCreatedAtDesc(mapId).stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    @Transactional
    public BigDecimal setComplexity(MapDifficulty mapDifficulty, BigDecimal complexity,
            String reason, Long authorId) {
        MapDifficultyComplexity current = complexityRepository
                .findActiveForUpdate(mapDifficulty.getId())
                .orElse(null);

        boolean isQueue = mapDifficulty.getStatus() == com.accsaber.backend.model.entity.map.MapDifficultyStatus.QUEUE;

        if (isQueue && current != null) {
            current.setComplexity(complexity);
            complexityRepository.saveAndFlush(current);
            return complexity;
        }

        if (current != null) {
            current.setActive(false);
            complexityRepository.saveAndFlush(current);
        }

        MapDifficultyComplexity newVersion = MapDifficultyComplexity.builder()
                .mapDifficulty(mapDifficulty)
                .complexity(complexity)
                .supersedes(current)
                .supersedesReason(reason)
                .supersedesAuthor(authorId)
                .active(true)
                .build();
        complexityRepository.saveAndFlush(newVersion);
        return complexity;
    }

    private MapComplexityHistoryResponse toHistoryResponse(MapDifficultyComplexity c) {
        MapDifficulty diff = c.getMapDifficulty();
        return MapComplexityHistoryResponse.builder()
                .id(c.getId())
                .mapDifficultyId(diff.getId())
                .difficulty(diff.getDifficulty())
                .characteristic(diff.getCharacteristic())
                .complexity(c.getComplexity())
                .reason(c.getSupersedesReason())
                .active(c.isActive())
                .supersedesId(c.getSupersedes() != null ? c.getSupersedes().getId() : null)
                .createdAt(c.getCreatedAt())
                .build();
    }
}
