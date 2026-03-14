package com.accsaber.backend.service.map;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.dto.response.map.MapDifficultyStatisticsResponse;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatistics;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.repository.map.MapDifficultyStatisticsRepository;
import com.accsaber.backend.repository.score.ScoreRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapDifficultyStatisticsService {

    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);

    private final MapDifficultyStatisticsRepository statisticsRepository;
    private final ScoreRepository scoreRepository;

    public Optional<MapDifficultyStatisticsResponse> findActive(UUID mapDifficultyId) {
        return statisticsRepository.findByMapDifficultyIdAndActiveTrue(mapDifficultyId)
                .map(MapDifficultyStatisticsService::toResponse);
    }

    public Map<UUID, MapDifficultyStatisticsResponse> findActiveForDifficulties(List<UUID> difficultyIds) {
        return statisticsRepository.findActiveByMapDifficultyIdIn(difficultyIds).stream()
                .collect(Collectors.toMap(
                        s -> s.getMapDifficulty().getId(),
                        MapDifficultyStatisticsService::toResponse));
    }

    @Transactional
    public void recalculate(MapDifficulty mapDifficulty, Long authorId) {
        List<Score> scores = scoreRepository.findByMapDifficulty_IdAndActiveTrue(mapDifficulty.getId());
        if (scores.isEmpty())
            return;

        BigDecimal maxAp = scores.stream().map(Score::getAp).max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal minAp = scores.stream().map(Score::getAp).min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal totalAp = scores.stream().map(Score::getAp).reduce(BigDecimal.ZERO, (a, b) -> a.add(b, MC));
        BigDecimal averageAp = totalAp.divide(BigDecimal.valueOf(scores.size()), 6, RoundingMode.HALF_UP);

        updateStatistics(mapDifficulty, maxAp, minAp, averageAp, scores.size(), authorId);
    }

    @Transactional
    public void updateStatistics(MapDifficulty mapDifficulty, BigDecimal maxAp, BigDecimal minAp,
            BigDecimal averageAp, int totalScores, Long authorId) {
        MapDifficultyStatistics current = statisticsRepository
                .findByMapDifficultyIdAndActiveTrue(mapDifficulty.getId())
                .orElse(null);

        if (current != null) {
            current.setActive(false);
            statisticsRepository.saveAndFlush(current);
        }

        MapDifficultyStatistics newVersion = MapDifficultyStatistics.builder()
                .mapDifficulty(mapDifficulty)
                .maxAp(maxAp)
                .minAp(minAp)
                .averageAp(averageAp)
                .totalScores(totalScores)
                .supersedes(current)
                .supersedesReason("Statistics recalculated")
                .supersedesAuthor(authorId)
                .active(true)
                .build();
        statisticsRepository.saveAndFlush(newVersion);
    }

    static MapDifficultyStatisticsResponse toResponse(MapDifficultyStatistics s) {
        return MapDifficultyStatisticsResponse.builder()
                .maxAp(s.getMaxAp())
                .minAp(s.getMinAp())
                .averageAp(s.getAverageAp())
                .totalScores(s.getTotalScores())
                .build();
    }
}
