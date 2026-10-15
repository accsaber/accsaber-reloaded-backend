package com.accsaber.backend.service.stats;

import static com.accsaber.backend.service.stats.StatsQueryRunner.mapIdentity;
import static com.accsaber.backend.service.stats.StatsQueryRunner.userIdentity;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.dto.response.statistics.MapAvgApResponse;
import com.accsaber.backend.model.dto.response.statistics.MapRetryResponse;
import com.accsaber.backend.model.dto.response.statistics.MilestoneCollectorResponse;
import com.accsaber.backend.model.dto.response.statistics.UserImprovementsResponse;
import com.accsaber.backend.model.dto.response.statistics.UserMapImprovementsResponse;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.service.score.ScoreQueryService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SiteStatisticsService {

        private final ScoreRepository scoreRepository;
        private final ScoreQueryService scoreQueryService;
        private final StatsQueryRunner queryRunner;

        @Cacheable(value = "statistics", key = "'streaks:' + #categoryId + ':' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<ScoreResponse> getTopStreaks(UUID categoryId, String country, Pageable pageable) {
                Pageable effective = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
                return hydrateScorePage(scoreRepository.findTopStreakIds(categoryId, StatsQueryRunner.normalizeCountry(country),
                                effective));
        }

        @Cacheable(value = "statistics", key = "'maxap:' + #categoryId + ':' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<ScoreResponse> getTopByAp(UUID categoryId, String country, Pageable pageable) {
                Pageable effective = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
                return hydrateScorePage(scoreRepository.findTopApIds(categoryId, StatsQueryRunner.normalizeCountry(country), effective));
        }

        private Page<ScoreResponse> hydrateScorePage(Page<UUID> ids) {
                if (ids.isEmpty())
                        return new PageImpl<>(List.of(), ids.getPageable(), ids.getTotalElements());

                Map<UUID, Score> byId = scoreRepository.findDetailedByIds(ids.getContent()).stream()
                                .collect(Collectors.toMap(Score::getId, s -> s));
                List<ScoreResponse> content = ids.getContent().stream()
                                .map(byId::get)
                                .filter(Objects::nonNull)
                                .map(scoreQueryService::mapToResponse)
                                .toList();
                return new PageImpl<>(content, ids.getPageable(), ids.getTotalElements());
        }

        @Cacheable(value = "statistics", key = "'highavgweightedap:' + #categoryId + ':' + #country + ':' + #minScores + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<MapAvgApResponse> getHighestAvgAp(UUID categoryId, String country, int minScores,
                        Pageable pageable) {
                Map<String, Object> params = new LinkedHashMap<>();
                String sql = SiteStatisticsQueries.highestAvgAp(categoryId, country, minScores, params);
                return queryRunner.paged(sql, params, pageable, row -> MapAvgApResponse.builder()
                                .map(mapIdentity(row, 0))
                                .averageWeightedAp((Double) row[11])
                                .scoreCount(((Number) row[12]).longValue())
                                .latestScoreTimeSet(row[13] != null ? (Instant) row[13] : null)
                                .latestScoreId(row[14] != null ? (UUID) row[14] : null)
                                .build());
        }

        @Cacheable(value = "statistics", key = "'mostretried:' + #categoryId + ':' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<MapRetryResponse> getMostRetriedMaps(UUID categoryId, String country, Pageable pageable) {
                Map<String, Object> params = new LinkedHashMap<>();
                String sql = SiteStatisticsQueries.mostRetriedMaps(categoryId, country, params);
                return queryRunner.paged(sql, params, pageable, row -> MapRetryResponse.builder()
                                .map(mapIdentity(row, 0))
                                .supersededCount(((Number) row[11]).longValue())
                                .latestScoreTimeSet(row[12] != null ? (Instant) row[12] : null)
                                .latestScoreId(row[13] != null ? (UUID) row[13] : null)
                                .build());
        }

        @Cacheable(value = "statistics", key = "'mostimprovements:' + #categoryId + ':' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<UserImprovementsResponse> getMostImprovements(UUID categoryId, String country, Pageable pageable) {
                Map<String, Object> params = new LinkedHashMap<>();
                String sql = SiteStatisticsQueries.mostImprovements(categoryId, country, params);
                return queryRunner.paged(sql, params, pageable, row -> UserImprovementsResponse.builder()
                                .user(userIdentity(row, 0))
                                .improvementCount(((Number) row[5]).longValue())
                                .latestScoreTimeSet(row[6] != null ? ((Instant) row[6]) : null)
                                .latestScoreId(row[7] != null ? (UUID) row[7] : null)
                                .build());
        }

        @Cacheable(value = "statistics", key = "'mostmapimprovements:' + #categoryId + ':' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<UserMapImprovementsResponse> getMostMapImprovements(UUID categoryId, String country,
                        Pageable pageable) {
                Map<String, Object> params = new LinkedHashMap<>();
                String sql = SiteStatisticsQueries.mostMapImprovements(categoryId, country, params);
                return queryRunner.paged(sql, params, pageable, row -> UserMapImprovementsResponse.builder()
                                .user(userIdentity(row, 0))
                                .map(mapIdentity(row, 5))
                                .improvementCount(((Number) row[16]).longValue())
                                .latestScoreTimeSet(row[17] != null ? ((Instant) row[17]) : null)
                                .latestScoreId(row[18] != null ? (UUID) row[18] : null)
                                .build());
        }

        @Cacheable(value = "statistics", key = "'milestonecollectors:' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<MilestoneCollectorResponse> getMilestoneCollectors(String country, Pageable pageable) {
                Map<String, Object> params = new LinkedHashMap<>();
                String sql = SiteStatisticsQueries.milestoneCollectors(country, params);
                return queryRunner.paged(sql, params, pageable, row -> MilestoneCollectorResponse.builder()
                                .user(userIdentity(row, 0))
                                .milestoneCount(((Number) row[5]).longValue())
                                .build());
        }
}
