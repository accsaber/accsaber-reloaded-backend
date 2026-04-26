package com.accsaber.backend.service.stats;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.dto.response.statistics.DistributionEntryResponse;
import com.accsaber.backend.model.dto.response.statistics.MapAvgApResponse;
import com.accsaber.backend.model.dto.response.statistics.MapRetryResponse;
import com.accsaber.backend.model.dto.response.statistics.MilestoneCollectorResponse;
import com.accsaber.backend.model.dto.response.statistics.TimeSeriesPointResponse;
import com.accsaber.backend.model.dto.response.statistics.UserImprovementsResponse;
import com.accsaber.backend.model.dto.response.statistics.UserMapImprovementsResponse;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.service.score.ScoreService;
import com.accsaber.backend.util.HmdMapper;
import com.accsaber.backend.util.TimeRangeUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SiteStatisticsService {

        private final ScoreRepository scoreRepository;
        private final ScoreService scoreService;
        private final EntityManager entityManager;

        @Cacheable(value = "statistics", key = "'streaks:' + #categoryId + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<ScoreResponse> getTopStreaks(UUID categoryId, Pageable pageable) {
                Pageable effective = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                                Sort.by(Sort.Direction.DESC, "streak115")
                                                .and(Sort.by(Sort.Direction.DESC, "ap"))
                                                .and(Sort.by(Sort.Direction.DESC, "score")));
                Page<Score> page = categoryId != null
                                ? scoreRepository.findTopStreaksByCategory(categoryId, effective)
                                : scoreRepository.findTopStreaks(effective);
                return page.map(scoreService::mapToResponse);
        }

        @Cacheable(value = "statistics", key = "'maxap:' + #categoryId + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<ScoreResponse> getTopByAp(UUID categoryId, Pageable pageable) {
                Pageable effective = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                                Sort.by(Sort.Direction.DESC, "ap")
                                                .and(Sort.by(Sort.Direction.DESC, "score")));
                Page<Score> page = categoryId != null
                                ? scoreRepository.findTopByApAndCategory(categoryId, effective)
                                : scoreRepository.findTopByAp(effective);
                return page.map(scoreService::mapToResponse);
        }

        @Cacheable(value = "statistics", key = "'highavgweightedap:' + #categoryId + ':' + #minScores + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<MapAvgApResponse> getHighestAvgAp(UUID categoryId, int minScores, Pageable pageable) {
                String sql = """
                                SELECT d.id, d.map_id, m.song_name, m.song_author, m.map_author, m.cover_url,
                                        d.difficulty, c.id AS cat_id, c.name AS cat_name,
                                        AVG(s.weighted_ap) AS avg_weighted_ap, COUNT(*) AS score_count,
                                        MAX(s.time_set) AS latest_time_set,
                                        (SELECT s2.id FROM scores s2 WHERE s2.map_difficulty_id = d.id
                                        AND s2.active = true ORDER BY s2.time_set DESC NULLS LAST LIMIT 1) AS latest_score_id
                                FROM scores s
                                JOIN map_difficulties d ON d.id = s.map_difficulty_id
                                JOIN maps m ON m.id = d.map_id
                                JOIN categories c ON c.id = d.category_id
                                JOIN users u ON u.id = s.user_id
                                WHERE s.active = true AND u.active = true AND u.banned = false
                                """;
                if (categoryId != null) {
                        sql += " AND d.category_id = :categoryId";
                }
                sql += " GROUP BY d.id, d.map_id, m.song_name, m.song_author, m.map_author, m.cover_url," +
                                " d.difficulty, c.id, c.name HAVING COUNT(*) >= :minScores" +
                                " ORDER BY avg_weighted_ap DESC, score_count DESC, m.song_name ASC";

                Map<String, Object> params = new LinkedHashMap<>();
                if (categoryId != null)
                        params.put("categoryId", categoryId);
                params.put("minScores", (long) minScores);

                return executePagedNativeQuery(sql, params, pageable, row -> MapAvgApResponse.builder()
                                .mapDifficultyId((UUID) row[0])
                                .mapId((UUID) row[1])
                                .songName((String) row[2])
                                .songAuthor((String) row[3])
                                .mapAuthor((String) row[4])
                                .coverUrl((String) row[5])
                                .difficulty(Difficulty.fromDbValue((String) row[6]))
                                .categoryId((UUID) row[7])
                                .categoryName((String) row[8])
                                .averageWeightedAp((BigDecimal) row[9])
                                .scoreCount(((Number) row[10]).longValue())
                                .latestScoreTimeSet(row[11] != null ? (Instant) row[11] : null)
                                .latestScoreId(row[12] != null ? (UUID) row[12] : null)
                                .build());
        }

        @Cacheable(value = "statistics", key = "'mostretried:' + #categoryId + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<MapRetryResponse> getMostRetriedMaps(UUID categoryId, Pageable pageable) {
                String sql = """
                                SELECT d.id, d.map_id, m.song_name, m.song_author, m.map_author, m.cover_url,
                                        d.difficulty, c.id AS cat_id, c.name AS cat_name,
                                        COUNT(*) AS superseded_count,
                                        MAX(s.time_set) AS latest_time_set,
                                        (SELECT s2.id FROM scores s2 WHERE s2.map_difficulty_id = d.id 
                                        AND s2.active = true ORDER BY s2.time_set DESC NULLS LAST LIMIT 1) AS latest_score_id
                                FROM scores s
                                JOIN map_difficulties d ON d.id = s.map_difficulty_id
                                JOIN maps m ON m.id = d.map_id
                                JOIN categories c ON c.id = d.category_id
                                JOIN users u ON u.id = s.user_id
                                WHERE s.active = false AND s.supersedes_reason = 'Score improved' AND u.active = true AND u.banned = false
                                """;
                if (categoryId != null) {
                        sql += " AND d.category_id = :categoryId";
                }
                sql += " GROUP BY d.id, d.map_id, m.song_name, m.song_author, m.map_author, m.cover_url," +
                                " d.difficulty, c.id, c.name ORDER BY superseded_count DESC, m.song_name ASC";

                Map<String, Object> params = categoryId != null ? Map.of("categoryId", categoryId) : Map.of();

                return executePagedNativeQuery(sql, params, pageable, row -> MapRetryResponse.builder()
                                .mapDifficultyId((UUID) row[0])
                                .mapId((UUID) row[1])
                                .songName((String) row[2])
                                .songAuthor((String) row[3])
                                .mapAuthor((String) row[4])
                                .coverUrl((String) row[5])
                                .difficulty(Difficulty.fromDbValue((String) row[6]))
                                .categoryId((UUID) row[7])
                                .categoryName((String) row[8])
                                .supersededCount(((Number) row[9]).longValue())
                                .latestScoreTimeSet(row[10] != null ? (Instant) row[10] : null)
                                .latestScoreId(row[11] != null ? (UUID) row[11] : null)
                                .build());
        }

        @Cacheable(value = "statistics", key = "'mostimprovements:' + #categoryId + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<UserImprovementsResponse> getMostImprovements(UUID categoryId, Pageable pageable) {
                String sql = """
                                SELECT u.id, u.name, u.avatar_url, u.country, COUNT(*) AS improvement_count,
                                        MAX(s.time_set) AS latest_time_set,
                                        (SELECT s2.id FROM scores s2 WHERE s2.user_id = u.id AND s2.active = true 
                                                ORDER BY s2.time_set DESC NULLS LAST LIMIT 1) AS latest_score_id
                                FROM scores s
                                JOIN users u ON u.id = s.user_id
                                """;
                if (categoryId != null) {
                        sql += " JOIN map_difficulties d ON d.id = s.map_difficulty_id";
                }
                sql += " WHERE s.active = false AND s.supersedes_reason = 'Score improved' AND u.active = true AND u.banned = false";
                if (categoryId != null) {
                        sql += " AND d.category_id = :categoryId";
                }
                sql += " GROUP BY u.id, u.name, u.avatar_url, u.country ORDER BY improvement_count DESC, u.name ASC";

                Map<String, Object> params = categoryId != null ? Map.of("categoryId", categoryId) : Map.of();

                return executePagedNativeQuery(sql, params, pageable, row -> UserImprovementsResponse.builder()
                                .userId(String.valueOf(((Number) row[0]).longValue()))
                                .userName((String) row[1])
                                .avatarUrl((String) row[2])
                                .country((String) row[3])
                                .improvementCount(((Number) row[4]).longValue())
                                .latestScoreTimeSet(row[5] != null ? ((Instant) row[5]) : null)
                                .latestScoreId(row[6] != null ? (UUID) row[6] : null)
                                .build());
        }

        @Cacheable(value = "statistics", key = "'mostmapimprovements:' + #categoryId + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<UserMapImprovementsResponse> getMostMapImprovements(UUID categoryId, Pageable pageable) {
                String sql = """
                                SELECT u.id, u.name, u.avatar_url, u.country,
                                        d.id AS diff_id, d.map_id, m.song_name, m.song_author, m.map_author, m.cover_url,
                                        d.difficulty, c.id AS cat_id, c.name AS cat_name,
                                        COUNT(*) AS improvement_count,
                                        MAX(s.time_set) AS latest_time_set,
                                        (SELECT s2.id FROM scores s2 WHERE s2.user_id = u.id AND s2.map_difficulty_id = d.id 
                                                AND s2.active = true LIMIT 1) AS latest_score_id
                                FROM scores s
                                JOIN users u ON u.id = s.user_id
                                JOIN map_difficulties d ON d.id = s.map_difficulty_id
                                JOIN maps m ON m.id = d.map_id
                                JOIN categories c ON c.id = d.category_id
                                WHERE s.active = false AND s.supersedes_reason = 'Score improved' 
                                        AND u.active = true AND u.banned = false
                                """;
                if (categoryId != null) {
                        sql += " AND d.category_id = :categoryId";
                }
                sql += " GROUP BY u.id, u.name, u.avatar_url, u.country," +
                                " d.id, d.map_id, m.song_name, m.song_author, m.map_author, m.cover_url," +
                                " d.difficulty, c.id, c.name ORDER BY improvement_count DESC, u.name ASC, m.song_name ASC";

                Map<String, Object> params = categoryId != null ? Map.of("categoryId", categoryId) : Map.of();

                return executePagedNativeQuery(sql, params, pageable, row -> UserMapImprovementsResponse.builder()
                                .userId(String.valueOf(((Number) row[0]).longValue()))
                                .userName((String) row[1])
                                .avatarUrl((String) row[2])
                                .country((String) row[3])
                                .mapDifficultyId((UUID) row[4])
                                .mapId((UUID) row[5])
                                .songName((String) row[6])
                                .songAuthor((String) row[7])
                                .mapAuthor((String) row[8])
                                .coverUrl((String) row[9])
                                .difficulty(Difficulty.fromDbValue((String) row[10]))
                                .categoryId((UUID) row[11])
                                .categoryName((String) row[12])
                                .improvementCount(((Number) row[13]).longValue())
                                .latestScoreTimeSet(row[14] != null ? ((Instant) row[14]) : null)
                                .latestScoreId(row[15] != null ? (UUID) row[15] : null)
                                .build());
        }

        @Cacheable(value = "statistics", key = "'milestonecollectors:' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<MilestoneCollectorResponse> getMilestoneCollectors(Pageable pageable) {
                String sql = """
                                SELECT u.id, u.name, u.avatar_url, u.country, COUNT(*) AS milestone_count
                                FROM user_milestone_links uml
                                JOIN users u ON u.id = uml.user_id
                                WHERE uml.completed = true AND u.active = true AND u.banned = false
                                GROUP BY u.id, u.name, u.avatar_url, u.country
                                ORDER BY milestone_count DESC, u.name ASC
                                """;

                return executePagedNativeQuery(sql, Map.of(), pageable, row -> MilestoneCollectorResponse.builder()
                                .userId(String.valueOf(((Number) row[0]).longValue()))
                                .userName((String) row[1])
                                .avatarUrl((String) row[2])
                                .country((String) row[3])
                                .milestoneCount(((Number) row[4]).longValue())
                                .build());
        }

        @Cacheable(value = "statistics", key = "'newplayersperday:' + #amount + ':' + #unit")
        public List<TimeSeriesPointResponse> getNewPlayersPerDay(int amount, String unit) {
                Instant since = TimeRangeUtil.computeSince(amount, unit);
                String trunc = TimeRangeUtil.granularity(since);
                String sql = "SELECT day, cnt FROM (" +
                                " SELECT date_trunc('" + trunc + "', created_at)::date AS day, COUNT(*) AS cnt" +
                                " FROM users WHERE active = true AND banned = false AND created_at >= :since" +
                                " GROUP BY day) sub WHERE cnt <= 4000 ORDER BY day";
                return executeTimeSeriesQuery(sql, since);
        }

        @Cacheable(value = "statistics", key = "'scoresperday:' + #amount + ':' + #unit")
        public List<TimeSeriesPointResponse> getScoresPerDay(int amount, String unit) {
                Instant since = TimeRangeUtil.computeSince(amount, unit);
                String trunc = TimeRangeUtil.granularity(since);
                String sql = "SELECT date_trunc('" + trunc + "', s.time_set)::date AS day, COUNT(*) AS cnt" +
                                " FROM scores s JOIN users u ON u.id = s.user_id" +
                                " WHERE u.active = true AND u.banned = false AND s.time_set >= :since" +
                                " GROUP BY day ORDER BY day";
                return executeTimeSeriesQuery(sql, since);
        }

        @Cacheable(value = "statistics", key = "'cumulativeaccounts:' + #amount + ':' + #unit")
        public List<TimeSeriesPointResponse> getCumulativeAccounts(int amount, String unit) {
                Instant since = TimeRangeUtil.computeSince(amount, unit);
                String trunc = TimeRangeUtil.granularity(since);
                String sql = "SELECT day, SUM(cnt) OVER (ORDER BY day) AS cumulative FROM (" +
                                " SELECT date_trunc('" + trunc + "', created_at)::date AS day, COUNT(*) AS cnt" +
                                " FROM users WHERE active = true AND banned = false GROUP BY day" +
                                ") daily WHERE day >= :since ORDER BY day";
                return executeTimeSeriesQuery(sql, since);
        }

        @Cacheable(value = "statistics", key = "'cumulativescores:' + #amount + ':' + #unit")
        public List<TimeSeriesPointResponse> getCumulativeScores(int amount, String unit) {
                Instant since = TimeRangeUtil.computeSince(amount, unit);
                String trunc = TimeRangeUtil.granularity(since);
                String sql = "SELECT day, SUM(cnt) OVER (ORDER BY day) AS cumulative FROM (" +
                                " SELECT date_trunc('" + trunc + "', time_set)::date AS day, COUNT(*) AS cnt" +
                                " FROM scores s JOIN users u ON u.id = s.user_id" +
                                " WHERE u.active = true AND u.banned = false GROUP BY day" +
                                ") daily WHERE day >= :since ORDER BY day";
                return executeTimeSeriesQuery(sql, since);
        }

        @Cacheable(value = "statistics", key = "'scorespercategory'")
        public List<DistributionEntryResponse> getScoresPerCategory() {
                String sql = """
                                SELECT c.name, COUNT(*) AS cnt
                                FROM scores s
                                JOIN map_difficulties d ON d.id = s.map_difficulty_id
                                JOIN categories c ON c.id = d.category_id
                                JOIN users u ON u.id = s.user_id
                                WHERE s.active = true AND u.active = true AND u.banned = false
                                GROUP BY c.name
                                ORDER BY cnt DESC
                                """;
                return executeDistributionQuery(sql);
        }

        @Cacheable(value = "statistics", key = "'playersbyhmd'")
        public List<DistributionEntryResponse> getPlayersByHmd() {
                String sql = """
                                SELECT hmd, COUNT(*) AS cnt FROM (
                                        SELECT DISTINCT ON (s.user_id) s.hmd
                                        FROM scores s
                                        JOIN users u ON u.id = s.user_id
                                        WHERE s.active = true AND u.active = true AND u.banned = false
                                                AND s.hmd IS NOT NULL AND s.hmd != '' AND s.hmd != '0'
                                        ORDER BY s.user_id, s.time_set DESC NULLS LAST
                                ) latest
                                GROUP BY hmd
                                ORDER BY cnt DESC
                                """;
                @SuppressWarnings("unchecked")
                List<Object[]> rows = entityManager.createNativeQuery(sql).getResultList();

                Map<String, Long> aggregated = new LinkedHashMap<>();
                for (Object[] row : rows) {
                        String label = HmdMapper.normalize((String) row[0]);
                        if (label == null)
                                continue;
                        aggregated.merge(label, ((Number) row[1]).longValue(), Long::sum);
                }

                return aggregated.entrySet().stream()
                                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                                .map(e -> DistributionEntryResponse.builder()
                                                .label(e.getKey())
                                                .count(e.getValue())
                                                .build())
                                .toList();
        }

        @Cacheable(value = "statistics", key = "'playerspercountry'")
        public List<DistributionEntryResponse> getPlayersPerCountry() {
                String sql = """
                                SELECT country, COUNT(*) AS cnt
                                FROM users
                                WHERE active = true AND banned = false AND country IS NOT NULL AND country != ''
                                GROUP BY country
                                ORDER BY cnt DESC
                                """;
                return executeDistributionQuery(sql);
        }

        @SuppressWarnings("unchecked")
        private <T> Page<T> executePagedNativeQuery(String sql, Map<String, Object> params, Pageable pageable,
                        java.util.function.Function<Object[], T> mapper) {

                String countSql = "SELECT COUNT(*) FROM (" + sql + ") _count";
                Query countQuery = entityManager.createNativeQuery(countSql);
                params.forEach(countQuery::setParameter);
                long total = ((Number) countQuery.getSingleResult()).longValue();

                Query dataQuery = entityManager.createNativeQuery(sql);
                params.forEach(dataQuery::setParameter);
                dataQuery.setFirstResult((int) pageable.getOffset());
                dataQuery.setMaxResults(pageable.getPageSize());

                List<Object[]> rows = dataQuery.getResultList();
                List<T> content = rows.stream().map(mapper).toList();

                return new PageImpl<>(content, pageable, total);
        }

        @SuppressWarnings("unchecked")
        private List<TimeSeriesPointResponse> executeTimeSeriesQuery(String sql, Instant since) {
                Query query = entityManager.createNativeQuery(sql);
                query.setParameter("since", since);
                List<Object[]> rows = query.getResultList();
                return rows.stream()
                                .map(row -> TimeSeriesPointResponse.builder()
                                                .date((java.time.LocalDate) row[0])
                                                .value(((Number) row[1]).longValue())
                                                .build())
                                .toList();
        }

        @SuppressWarnings("unchecked")
        private List<DistributionEntryResponse> executeDistributionQuery(String sql) {
                List<Object[]> rows = entityManager.createNativeQuery(sql).getResultList();
                return rows.stream()
                                .map(row -> DistributionEntryResponse.builder()
                                                .label((String) row[0])
                                                .count(((Number) row[1]).longValue())
                                                .build())
                                .toList();
        }
}
