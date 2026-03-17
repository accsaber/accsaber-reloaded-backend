package com.accsaber.backend.service.map;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.dto.response.map.MapDifficultyStatisticsResponse;
import com.accsaber.backend.model.dto.response.map.TopScoreSnapshot;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatistics;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.map.MapDifficultyStatisticsRepository;
import com.accsaber.backend.repository.score.ScoreRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapDifficultyStatisticsService {

    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);
    private static final int ACCURACY_SCALE = 10;

    private final MapDifficultyStatisticsRepository statisticsRepository;
    private final ScoreRepository scoreRepository;

    public Optional<MapDifficultyStatisticsResponse> findActive(UUID mapDifficultyId) {
        Optional<MapDifficultyStatistics> stats = statisticsRepository
                .findByMapDifficultyIdAndActiveTrue(mapDifficultyId);
        if (stats.isEmpty())
            return Optional.empty();

        TopScoreSnapshot topScore = scoreRepository.findCurrentTopOne(mapDifficultyId)
                .map(this::toTopScoreSnapshot)
                .orElse(null);

        return Optional.of(toResponse(stats.get(), topScore));
    }

    public Map<UUID, MapDifficultyStatisticsResponse> findActiveForDifficulties(List<UUID> difficultyIds) {
        return statisticsRepository.findActiveByMapDifficultyIdIn(difficultyIds).stream()
                .collect(Collectors.toMap(
                        s -> s.getMapDifficulty().getId(),
                        s -> toResponse(s, null)));
    }

    public List<MapDifficultyStatisticsResponse> findHistoric(UUID mapDifficultyId, int amount, String unit) {
        Instant since = ZonedDateTime.now(ZoneOffset.UTC).minus(amount, parseUnit(unit)).toInstant();

        List<MapDifficultyStatistics> stats = statisticsRepository
                .findHistoricDownsampled(mapDifficultyId, since);
        List<Score> topOnes = scoreRepository.findTopOneHistory(mapDifficultyId, since);

        return mergeStatsWithTopScores(stats, topOnes);
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

    private List<MapDifficultyStatisticsResponse> mergeStatsWithTopScores(
            List<MapDifficultyStatistics> stats, List<Score> topOnes) {
        List<MapDifficultyStatisticsResponse> result = new ArrayList<>();
        int topIdx = 0;
        TopScoreSnapshot currentTop = null;

        for (MapDifficultyStatistics s : stats) {
            while (topIdx < topOnes.size()
                    && !topOnes.get(topIdx).getCreatedAt().isAfter(s.getCreatedAt())) {
                currentTop = toTopScoreSnapshot(topOnes.get(topIdx));
                topIdx++;
            }
            result.add(toResponse(s, currentTop));
        }

        return result;
    }

    private TopScoreSnapshot toTopScoreSnapshot(Score s) {
        User user = s.getUser();
        Integer maxScore = s.getMapDifficulty().getMaxScore();
        BigDecimal accuracy = maxScore != null && maxScore > 0
                ? BigDecimal.valueOf(s.getScore()).divide(BigDecimal.valueOf(maxScore), ACCURACY_SCALE,
                        RoundingMode.HALF_UP)
                : null;

        return TopScoreSnapshot.builder()
                .scoreId(s.getId())
                .userId(String.valueOf(user.getId()))
                .userName(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .score(s.getScore())
                .accuracy(accuracy)
                .ap(s.getAp())
                .timeSet(s.getTimeSet())
                .build();
    }

    private ChronoUnit parseUnit(String unit) {
        return switch (unit.toLowerCase()) {
            case "h" -> ChronoUnit.HOURS;
            case "d" -> ChronoUnit.DAYS;
            case "w" -> ChronoUnit.WEEKS;
            case "mo" -> ChronoUnit.MONTHS;
            default -> throw new IllegalArgumentException("Invalid time unit: " + unit + ". Use h, d, w, or mo");
        };
    }

    static MapDifficultyStatisticsResponse toResponse(MapDifficultyStatistics s, TopScoreSnapshot topScore) {
        return MapDifficultyStatisticsResponse.builder()
                .id(s.getId())
                .maxAp(s.getMaxAp())
                .minAp(s.getMinAp())
                .averageAp(s.getAverageAp())
                .totalScores(s.getTotalScores())
                .topScore(topScore)
                .createdAt(s.getCreatedAt())
                .build();
    }
}
