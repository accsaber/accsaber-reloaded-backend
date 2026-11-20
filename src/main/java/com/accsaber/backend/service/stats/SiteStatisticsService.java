package com.accsaber.backend.service.stats;

import static com.accsaber.backend.service.stats.StatsQueryRunner.mapRef;
import static com.accsaber.backend.service.stats.StatsQueryRunner.playerRef;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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

        @CachedSiteStatistic(key = "'streaks:' + #categoryId + ':' + #filter.country() + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<ScoreResponse> getTopStreaks(UUID categoryId, SiteStatsFilter filter, Pageable pageable) {
                return topScores(filter, pageable, page -> scoreRepository.findTopStreakIds(categoryId,
                                filter.country(), filter.userIds(), page));
        }

        @CachedSiteStatistic(key = "'maxap:' + #categoryId + ':' + #filter.country() + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<ScoreResponse> getTopByAp(UUID categoryId, SiteStatsFilter filter, Pageable pageable) {
                return topScores(filter, pageable, page -> scoreRepository.findTopApIds(categoryId,
                                filter.country(), filter.userIds(), page));
        }

        private Page<ScoreResponse> topScores(SiteStatsFilter filter, Pageable pageable,
                        Function<Pageable, Page<UUID>> ids) {
                Pageable effective = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
                return hydrateScorePage(filter.matchesNobody() ? Page.empty(effective) : ids.apply(effective));
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

        @CachedSiteStatistic(key = "'highavgweightedap:' + #categoryId + ':' + #filter.country() + ':' + #minScores + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<MapAvgApResponse> getHighestAvgAp(UUID categoryId, SiteStatsFilter filter, int minScores,
                        Pageable pageable) {
                return queryRunner.paged(
                        params -> SiteStatisticsQueries.highestAvgAp(categoryId, filter, minScores, params), pageable,
                        row -> MapAvgApResponse.builder()
                                        .map(mapRef(row, 0))
                                        .averageWeightedAp((Double) row[13])
                                        .scoreCount(((Number) row[14]).longValue())
                                        .latestScoreTimeSet(row[15] != null ? (Instant) row[15] : null)
                                        .latestScoreId(row[16] != null ? (UUID) row[16] : null)
                                        .build());
        }

        @CachedSiteStatistic(key = "'mostretried:' + #categoryId + ':' + #filter.country() + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<MapRetryResponse> getMostRetriedMaps(UUID categoryId, SiteStatsFilter filter, Pageable pageable) {
                return queryRunner.paged(
                        params -> SiteStatisticsQueries.mostRetriedMaps(categoryId, filter, params), pageable,
                        row -> MapRetryResponse.builder()
                                        .map(mapRef(row, 0))
                                        .supersededCount(((Number) row[13]).longValue())
                                        .latestScoreTimeSet(row[14] != null ? (Instant) row[14] : null)
                                        .latestScoreId(row[15] != null ? (UUID) row[15] : null)
                                        .build());
        }

        @CachedSiteStatistic(key = "'mostimprovements:' + #categoryId + ':' + #filter.country() + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<UserImprovementsResponse> getMostImprovements(UUID categoryId, SiteStatsFilter filter,
                        Pageable pageable) {
                return queryRunner.paged(
                        params -> SiteStatisticsQueries.mostImprovements(categoryId, filter, params), pageable,
                        row -> UserImprovementsResponse.builder()
                                        .player(playerRef(row, 0))
                                        .improvementCount(((Number) row[5]).longValue())
                                        .latestScoreTimeSet(row[6] != null ? ((Instant) row[6]) : null)
                                        .latestScoreId(row[7] != null ? (UUID) row[7] : null)
                                        .build());
        }

        @CachedSiteStatistic(key = "'mostmapimprovements:' + #categoryId + ':' + #filter.country() + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<UserMapImprovementsResponse> getMostMapImprovements(UUID categoryId, SiteStatsFilter filter,
                        Pageable pageable) {
                return queryRunner.paged(
                        params -> SiteStatisticsQueries.mostMapImprovements(categoryId, filter, params), pageable,
                        row -> UserMapImprovementsResponse.builder()
                                        .player(playerRef(row, 0))
                                        .map(mapRef(row, 5))
                                        .improvementCount(((Number) row[18]).longValue())
                                        .latestScoreTimeSet(row[19] != null ? ((Instant) row[19]) : null)
                                        .latestScoreId(row[20] != null ? (UUID) row[20] : null)
                                        .build());
        }

        @CachedSiteStatistic(key = "'milestonecollectors:' + #filter.country() + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<MilestoneCollectorResponse> getMilestoneCollectors(SiteStatsFilter filter, Pageable pageable) {
                return queryRunner.paged(params -> SiteStatisticsQueries.milestoneCollectors(filter, params), pageable,
                        row -> MilestoneCollectorResponse.builder()
                                        .player(playerRef(row, 0))
                                        .milestoneCount(((Number) row[5]).longValue())
                                        .build());
        }
}
