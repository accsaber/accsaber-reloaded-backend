package com.accsaber.backend.service.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.dto.response.map.MapComplexityHistoryResponse;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyComplexity;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.map.ReweightRound;
import com.accsaber.backend.repository.map.MapDifficultyComplexityRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapDifficultyComplexityService {

    private final MapDifficultyComplexityRepository complexityRepository;

    public Optional<Double> findActiveComplexity(UUID mapDifficultyId) {
        return complexityRepository.findByMapDifficultyIdAndActiveTrue(mapDifficultyId)
                .map(MapDifficultyComplexity::getComplexity);
    }

    public Map<UUID, Double> findActiveComplexitiesForDifficulties(List<UUID> difficultyIds) {
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
    public Double setComplexity(MapDifficulty mapDifficulty, Double complexity,
            String reason, Long authorId) {
        MapDifficultyComplexity current = complexityRepository
                .findActiveForUpdate(mapDifficulty.getId())
                .orElse(null);

        boolean unranked = mapDifficulty.getStatus() != MapDifficultyStatus.RANKED;

        if (unranked && current != null) {
            current.setComplexity(complexity);
            current.setSupersedesReason(reason);
            current.setSupersedesAuthor(authorId);
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

    public record RankedChange(MapDifficulty difficulty, MapDifficultyComplexity current, double complexity,
            String reason) {

        public Double from() {
            return current == null ? null : current.getComplexity();
        }
    }

    @Transactional
    public Map<UUID, MapDifficultyComplexity> lockActive(List<UUID> difficultyIds) {
        return complexityRepository.findActiveForUpdateIn(difficultyIds).stream()
                .collect(Collectors.toMap(c -> c.getMapDifficulty().getId(), Function.identity()));
    }

    @Transactional
    public void supersedeAll(List<RankedChange> changes, Map<UUID, ReweightRound> roundsByCategory, Long authorId) {
        List<MapDifficultyComplexity> rows = new ArrayList<>(changes.size() * 2);
        for (RankedChange change : changes) {
            if (change.current() != null) {
                change.current().setActive(false);
                rows.add(change.current());
            }
            rows.add(MapDifficultyComplexity.builder()
                    .mapDifficulty(change.difficulty())
                    .complexity(change.complexity())
                    .supersedes(change.current())
                    .supersedesReason(change.reason())
                    .supersedesAuthor(authorId)
                    .round(roundsByCategory.get(change.difficulty().getCategory().getId()))
                    .active(true)
                    .build());
        }
        complexityRepository.saveAll(rows);
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
