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

import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.model.dto.request.item.ItemHolderSort;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.dto.response.statistics.BiggestTraderResponse;
import com.accsaber.backend.model.dto.response.statistics.CollectionCompletionResponse;
import com.accsaber.backend.model.dto.response.statistics.DistributionEntryResponse;
import com.accsaber.backend.model.dto.response.statistics.EssenceEarnedResponse;
import com.accsaber.backend.model.dto.response.statistics.FirstEditionHolderResponse;
import com.accsaber.backend.model.dto.response.statistics.FirstEditionsResponse;
import com.accsaber.backend.model.dto.response.statistics.InventoryValueResponse;
import com.accsaber.backend.model.dto.response.statistics.ItemHolderResponse;
import com.accsaber.backend.model.dto.response.statistics.ItemScarcityResponse;
import com.accsaber.backend.model.dto.response.statistics.MapAvgApResponse;
import com.accsaber.backend.model.dto.response.statistics.MapRetryResponse;
import com.accsaber.backend.model.dto.response.statistics.MilestoneCollectorResponse;
import com.accsaber.backend.model.dto.response.statistics.MostCratesOpenedResponse;
import com.accsaber.backend.model.dto.response.statistics.MostItemsResponse;
import com.accsaber.backend.model.dto.response.statistics.RarestUnboxedResponse;
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

        @Cacheable(value = "statistics", key = "'streaks:' + #categoryId + ':' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<ScoreResponse> getTopStreaks(UUID categoryId, String country, Pageable pageable) {
                Pageable effective = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                                Sort.by(Sort.Direction.DESC, "streak115")
                                                .and(Sort.by(Sort.Direction.DESC, "ap"))
                                                .and(Sort.by(Sort.Direction.DESC, "score")));
                return scoreRepository.findTopStreaks(categoryId, normalizeCountry(country), effective)
                                .map(scoreService::mapToResponse);
        }

        @Cacheable(value = "statistics", key = "'maxap:' + #categoryId + ':' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<ScoreResponse> getTopByAp(UUID categoryId, String country, Pageable pageable) {
                Pageable effective = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                                Sort.by(Sort.Direction.DESC, "ap")
                                                .and(Sort.by(Sort.Direction.DESC, "score")));
                return scoreRepository.findTopByAp(categoryId, normalizeCountry(country), effective)
                                .map(scoreService::mapToResponse);
        }

        @Cacheable(value = "statistics", key = "'highavgweightedap:' + #categoryId + ':' + #country + ':' + #minScores + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<MapAvgApResponse> getHighestAvgAp(UUID categoryId, String country, int minScores,
                        Pageable pageable) {
                String normalizedCountry = normalizeCountry(country);
                String sql = """
                                SELECT d.id, d.map_id, m.song_name, m.song_author, m.map_author, m.cover_url, m.cdn_cover_url,
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
                if (normalizedCountry != null) {
                        sql += " AND LOWER(u.country) = LOWER(:country)";
                }
                sql += " GROUP BY d.id, d.map_id, m.song_name, m.song_author, m.map_author, m.cover_url, m.cdn_cover_url," +
                                " d.difficulty, c.id, c.name HAVING COUNT(*) >= :minScores" +
                                " ORDER BY avg_weighted_ap DESC, score_count DESC, m.song_name ASC";

                Map<String, Object> params = new LinkedHashMap<>();
                if (categoryId != null)
                        params.put("categoryId", categoryId);
                if (normalizedCountry != null)
                        params.put("country", normalizedCountry);
                params.put("minScores", (long) minScores);

                return executePagedNativeQuery(sql, params, pageable, row -> MapAvgApResponse.builder()
                                .mapDifficultyId((UUID) row[0])
                                .mapId((UUID) row[1])
                                .songName((String) row[2])
                                .songAuthor((String) row[3])
                                .mapAuthor((String) row[4])
                                .coverUrl((String) row[5])
                                .cdnCoverUrl((String) row[6])
                                .difficulty(Difficulty.fromDbValue((String) row[7]))
                                .categoryId((UUID) row[8])
                                .categoryName((String) row[9])
                                .averageWeightedAp((BigDecimal) row[10])
                                .scoreCount(((Number) row[11]).longValue())
                                .latestScoreTimeSet(row[12] != null ? (Instant) row[12] : null)
                                .latestScoreId(row[13] != null ? (UUID) row[13] : null)
                                .build());
        }

        @Cacheable(value = "statistics", key = "'mostretried:' + #categoryId + ':' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<MapRetryResponse> getMostRetriedMaps(UUID categoryId, String country, Pageable pageable) {
                String normalizedCountry = normalizeCountry(country);
                String sql = """
                                SELECT d.id, d.map_id, m.song_name, m.song_author, m.map_author, m.cover_url, m.cdn_cover_url,
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
                if (normalizedCountry != null) {
                        sql += " AND LOWER(u.country) = LOWER(:country)";
                }
                sql += " GROUP BY d.id, d.map_id, m.song_name, m.song_author, m.map_author, m.cover_url, m.cdn_cover_url," +
                                " d.difficulty, c.id, c.name ORDER BY superseded_count DESC, m.song_name ASC";

                Map<String, Object> params = new LinkedHashMap<>();
                if (categoryId != null)
                        params.put("categoryId", categoryId);
                if (normalizedCountry != null)
                        params.put("country", normalizedCountry);

                return executePagedNativeQuery(sql, params, pageable, row -> MapRetryResponse.builder()
                                .mapDifficultyId((UUID) row[0])
                                .mapId((UUID) row[1])
                                .songName((String) row[2])
                                .songAuthor((String) row[3])
                                .mapAuthor((String) row[4])
                                .coverUrl((String) row[5])
                                .cdnCoverUrl((String) row[6])
                                .difficulty(Difficulty.fromDbValue((String) row[7]))
                                .categoryId((UUID) row[8])
                                .categoryName((String) row[9])
                                .supersededCount(((Number) row[10]).longValue())
                                .latestScoreTimeSet(row[11] != null ? (Instant) row[11] : null)
                                .latestScoreId(row[12] != null ? (UUID) row[12] : null)
                                .build());
        }

        @Cacheable(value = "statistics", key = "'mostimprovements:' + #categoryId + ':' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<UserImprovementsResponse> getMostImprovements(UUID categoryId, String country, Pageable pageable) {
                String normalizedCountry = normalizeCountry(country);
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
                if (normalizedCountry != null) {
                        sql += " AND LOWER(u.country) = LOWER(:country)";
                }
                sql += " GROUP BY u.id, u.name, u.avatar_url, u.country ORDER BY improvement_count DESC, u.name ASC";

                Map<String, Object> params = new LinkedHashMap<>();
                if (categoryId != null)
                        params.put("categoryId", categoryId);
                if (normalizedCountry != null)
                        params.put("country", normalizedCountry);

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

        @Cacheable(value = "statistics", key = "'mostmapimprovements:' + #categoryId + ':' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<UserMapImprovementsResponse> getMostMapImprovements(UUID categoryId, String country,
                        Pageable pageable) {
                String normalizedCountry = normalizeCountry(country);
                String sql = """
                                SELECT u.id, u.name, u.avatar_url, u.country,
                                        d.id AS diff_id, d.map_id, m.song_name, m.song_author, m.map_author, m.cover_url, m.cdn_cover_url,
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
                if (normalizedCountry != null) {
                        sql += " AND LOWER(u.country) = LOWER(:country)";
                }
                sql += " GROUP BY u.id, u.name, u.avatar_url, u.country," +
                                " d.id, d.map_id, m.song_name, m.song_author, m.map_author, m.cover_url, m.cdn_cover_url," +
                                " d.difficulty, c.id, c.name ORDER BY improvement_count DESC, u.name ASC, m.song_name ASC";

                Map<String, Object> params = new LinkedHashMap<>();
                if (categoryId != null)
                        params.put("categoryId", categoryId);
                if (normalizedCountry != null)
                        params.put("country", normalizedCountry);

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
                                .cdnCoverUrl((String) row[10])
                                .difficulty(Difficulty.fromDbValue((String) row[11]))
                                .categoryId((UUID) row[12])
                                .categoryName((String) row[13])
                                .improvementCount(((Number) row[14]).longValue())
                                .latestScoreTimeSet(row[15] != null ? ((Instant) row[15]) : null)
                                .latestScoreId(row[16] != null ? (UUID) row[16] : null)
                                .build());
        }

        @Cacheable(value = "statistics", key = "'milestonecollectors:' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<MilestoneCollectorResponse> getMilestoneCollectors(String country, Pageable pageable) {
                String normalizedCountry = normalizeCountry(country);
                String sql = """
                                SELECT u.id, u.name, u.avatar_url, u.country, COUNT(*) AS milestone_count
                                FROM user_milestone_links uml
                                JOIN users u ON u.id = uml.user_id
                                WHERE uml.completed = true AND u.active = true AND u.banned = false
                                """;
                if (normalizedCountry != null) {
                        sql += " AND LOWER(u.country) = LOWER(:country)";
                }
                sql += " GROUP BY u.id, u.name, u.avatar_url, u.country" +
                                " ORDER BY milestone_count DESC, u.name ASC";

                Map<String, Object> params = normalizedCountry != null ? Map.of("country", normalizedCountry) : Map.of();

                return executePagedNativeQuery(sql, params, pageable, row -> MilestoneCollectorResponse.builder()
                                .userId(String.valueOf(((Number) row[0]).longValue()))
                                .userName((String) row[1])
                                .avatarUrl((String) row[2])
                                .country((String) row[3])
                                .milestoneCount(((Number) row[4]).longValue())
                                .build());
        }

        @Cacheable(value = "statistics", key = "'newplayersperday:' + #amount + ':' + #unit + ':' + #country")
        public List<TimeSeriesPointResponse> getNewPlayersPerDay(int amount, String unit, String country) {
                Instant since = TimeRangeUtil.computeSince(amount, unit);
                String trunc = TimeRangeUtil.granularity(since);
                String normalizedCountry = normalizeCountry(country);
                String countryClause = normalizedCountry != null ? " AND LOWER(country) = LOWER(:country)" : "";
                String sql = "SELECT day, cnt FROM (" +
                                " SELECT date_trunc('" + trunc + "', created_at)::date AS day, COUNT(*) AS cnt" +
                                " FROM users WHERE active = true AND banned = false AND created_at >= :since" +
                                countryClause +
                                " GROUP BY day) sub WHERE cnt <= 4000 ORDER BY day";
                return executeTimeSeriesQuery(sql, since, normalizedCountry);
        }

        @Cacheable(value = "statistics", key = "'scoresperday:' + #amount + ':' + #unit + ':' + #country")
        public List<TimeSeriesPointResponse> getScoresPerDay(int amount, String unit, String country) {
                Instant since = TimeRangeUtil.computeSince(amount, unit);
                String trunc = TimeRangeUtil.granularity(since);
                String normalizedCountry = normalizeCountry(country);
                String countryClause = normalizedCountry != null ? " AND LOWER(u.country) = LOWER(:country)" : "";
                String sql = "SELECT date_trunc('" + trunc + "', s.time_set)::date AS day, COUNT(*) AS cnt" +
                                " FROM scores s JOIN users u ON u.id = s.user_id" +
                                " WHERE u.active = true AND u.banned = false AND s.time_set >= :since" +
                                countryClause +
                                " GROUP BY day ORDER BY day";
                return executeTimeSeriesQuery(sql, since, normalizedCountry);
        }

        @Cacheable(value = "statistics", key = "'cumulativeaccounts:' + #amount + ':' + #unit + ':' + #country")
        public List<TimeSeriesPointResponse> getCumulativeAccounts(int amount, String unit, String country) {
                Instant since = TimeRangeUtil.computeSince(amount, unit);
                String trunc = TimeRangeUtil.granularity(since);
                String normalizedCountry = normalizeCountry(country);
                String countryClause = normalizedCountry != null ? " AND LOWER(country) = LOWER(:country)" : "";
                String sql = "SELECT day, SUM(cnt) OVER (ORDER BY day) AS cumulative FROM (" +
                                " SELECT date_trunc('" + trunc + "', created_at)::date AS day, COUNT(*) AS cnt" +
                                " FROM users WHERE active = true AND banned = false" + countryClause + " GROUP BY day" +
                                ") daily WHERE day >= :since ORDER BY day";
                return executeTimeSeriesQuery(sql, since, normalizedCountry);
        }

        @Cacheable(value = "statistics", key = "'cumulativescores:' + #amount + ':' + #unit + ':' + #country")
        public List<TimeSeriesPointResponse> getCumulativeScores(int amount, String unit, String country) {
                Instant since = TimeRangeUtil.computeSince(amount, unit);
                String trunc = TimeRangeUtil.granularity(since);
                String normalizedCountry = normalizeCountry(country);
                String countryClause = normalizedCountry != null ? " AND LOWER(u.country) = LOWER(:country)" : "";
                String sql = "SELECT day, SUM(cnt) OVER (ORDER BY day) AS cumulative FROM (" +
                                " SELECT date_trunc('" + trunc + "', time_set)::date AS day, COUNT(*) AS cnt" +
                                " FROM scores s JOIN users u ON u.id = s.user_id" +
                                " WHERE u.active = true AND u.banned = false" + countryClause + " GROUP BY day" +
                                ") daily WHERE day >= :since ORDER BY day";
                return executeTimeSeriesQuery(sql, since, normalizedCountry);
        }

        @Cacheable(value = "statistics", key = "'scorespercategory:' + #country")
        public List<DistributionEntryResponse> getScoresPerCategory(String country) {
                String normalizedCountry = normalizeCountry(country);
                String sql = "SELECT c.name, COUNT(*) AS cnt" +
                                " FROM scores s" +
                                " JOIN map_difficulties d ON d.id = s.map_difficulty_id" +
                                " JOIN categories c ON c.id = d.category_id" +
                                " JOIN users u ON u.id = s.user_id" +
                                " WHERE s.active = true AND u.active = true AND u.banned = false" +
                                (normalizedCountry != null ? " AND LOWER(u.country) = LOWER(:country)" : "") +
                                " GROUP BY c.name ORDER BY cnt DESC";
                return executeDistributionQuery(sql,
                                normalizedCountry != null ? Map.of("country", normalizedCountry) : Map.of());
        }

        @Cacheable(value = "statistics", key = "'playersbyhmd:' + #country")
        public List<DistributionEntryResponse> getPlayersByHmd(String country) {
                String normalizedCountry = normalizeCountry(country);
                String sql = "SELECT hmd, COUNT(*) AS cnt FROM (" +
                                " SELECT DISTINCT ON (s.user_id) s.hmd" +
                                " FROM scores s" +
                                " JOIN users u ON u.id = s.user_id" +
                                " WHERE s.active = true AND u.active = true AND u.banned = false" +
                                " AND s.hmd IS NOT NULL AND s.hmd != '' AND s.hmd != '0'" +
                                (normalizedCountry != null ? " AND LOWER(u.country) = LOWER(:country)" : "") +
                                " ORDER BY s.user_id, s.time_set DESC NULLS LAST" +
                                ") latest GROUP BY hmd ORDER BY cnt DESC";
                Query nativeQuery = entityManager.createNativeQuery(sql);
                if (normalizedCountry != null)
                        nativeQuery.setParameter("country", normalizedCountry);
                @SuppressWarnings("unchecked")
                List<Object[]> rows = nativeQuery.getResultList();

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
                return executeDistributionQuery(sql, Map.of());
        }

        @Cacheable(value = "statistics", key = "'mostitems:' + #type + ':' + #modifier + ':' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<MostItemsResponse> getMostItems(String type, String modifier, String country, Pageable pageable) {
                String normalizedCountry = normalizeCountry(country);
                String sql = """
                                SELECT u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country, SUM(l.quantity) AS item_count
                                FROM user_item_links l
                                JOIN items i ON i.id = l.item_id
                                JOIN item_types t ON t.id = i.type_id
                                JOIN users u ON u.id = l.user_id
                                WHERE i.tradeable = true AND u.active = true AND u.banned = false
                                """;
                if (type != null)
                        sql += " AND t.key = :type";
                if (modifier != null)
                        sql += " AND EXISTS (SELECT 1 FROM user_item_link_modifiers lm" +
                                        " JOIN item_modifiers im ON im.id = lm.modifier_id" +
                                        " WHERE lm.user_item_link_id = l.id AND im.key = :modifier)";
                if (normalizedCountry != null)
                        sql += " AND LOWER(u.country) = LOWER(:country)";
                sql += " GROUP BY u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country" +
                                " ORDER BY item_count DESC, u.name ASC";

                Map<String, Object> params = new LinkedHashMap<>();
                if (type != null)
                        params.put("type", type);
                if (modifier != null)
                        params.put("modifier", modifier);
                if (normalizedCountry != null)
                        params.put("country", normalizedCountry);

                return executePagedNativeQuery(sql, params, pageable, row -> MostItemsResponse.builder()
                                .userId(String.valueOf(((Number) row[0]).longValue()))
                                .userName((String) row[1])
                                .avatarUrl((String) row[2])
                                .cdnAvatarUrl((String) row[3])
                                .country((String) row[4])
                                .itemCount(((Number) row[5]).longValue())
                                .build());
        }

        @Cacheable(value = "statistics", key = "'mostcrates:' + #crateId + ':' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<MostCratesOpenedResponse> getMostCratesOpened(UUID crateId, String country, Pageable pageable) {
                String normalizedCountry = normalizeCountry(country);
                String sql = """
                                SELECT u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country, COUNT(*) AS crate_count
                                FROM user_crate_opens o
                                JOIN users u ON u.id = o.user_id
                                WHERE u.active = true AND u.banned = false
                                """;
                if (crateId != null)
                        sql += " AND o.crate_item_id = :crateId";
                if (normalizedCountry != null)
                        sql += " AND LOWER(u.country) = LOWER(:country)";
                sql += " GROUP BY u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country" +
                                " ORDER BY crate_count DESC, u.name ASC";

                Map<String, Object> params = new LinkedHashMap<>();
                if (crateId != null)
                        params.put("crateId", crateId);
                if (normalizedCountry != null)
                        params.put("country", normalizedCountry);

                return executePagedNativeQuery(sql, params, pageable, row -> MostCratesOpenedResponse.builder()
                                .userId(String.valueOf(((Number) row[0]).longValue()))
                                .userName((String) row[1])
                                .avatarUrl((String) row[2])
                                .cdnAvatarUrl((String) row[3])
                                .country((String) row[4])
                                .crateCount(((Number) row[5]).longValue())
                                .build());
        }

        @Cacheable(value = "statistics", key = "'rarestunboxed:' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<RarestUnboxedResponse> getRarestUnboxed(String country, Pageable pageable) {
                String normalizedCountry = normalizeCountry(country);
                String sql = """
                                SELECT l.id, u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country,
                                        i.id, i.name, i.icon_url, i.rarity, t.key, l.serial_number,
                                        COUNT(lm.modifier_id) AS modifier_count,
                                        STRING_AGG(DISTINCT im.key, ',') AS modifier_keys,
                                        ue.name AS unusual_effect
                                FROM user_item_links l
                                JOIN items i ON i.id = l.item_id
                                JOIN item_types t ON t.id = i.type_id
                                JOIN users u ON u.id = l.user_id
                                LEFT JOIN user_item_link_modifiers lm ON lm.user_item_link_id = l.id
                                LEFT JOIN item_modifiers im ON im.id = lm.modifier_id
                                LEFT JOIN unusual_effects ue ON ue.id = l.unusual_effect_id
                                WHERE l.source = 'crate_drop' AND i.tradeable = true
                                        AND u.active = true AND u.banned = false
                                """;
                if (normalizedCountry != null)
                        sql += " AND LOWER(u.country) = LOWER(:country)";
                sql += " GROUP BY l.id, u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country," +
                                " i.id, i.name, i.icon_url, i.rarity, t.key, l.serial_number, ue.name" +
                                " ORDER BY modifier_count DESC, " + rarityRank("i.rarity") + " DESC," +
                                " l.serial_number ASC NULLS LAST";

                Map<String, Object> params = normalizedCountry != null ? Map.of("country", normalizedCountry) : Map.of();

                return executePagedNativeQuery(sql, params, pageable, row -> RarestUnboxedResponse.builder()
                                .linkId((UUID) row[0])
                                .userId(String.valueOf(((Number) row[1]).longValue()))
                                .userName((String) row[2])
                                .avatarUrl((String) row[3])
                                .cdnAvatarUrl((String) row[4])
                                .country((String) row[5])
                                .itemId((UUID) row[6])
                                .itemName((String) row[7])
                                .iconUrl((String) row[8])
                                .rarity((String) row[9])
                                .typeKey((String) row[10])
                                .serialNumber(row[11] != null ? ((Number) row[11]).longValue() : null)
                                .modifierCount(((Number) row[12]).longValue())
                                .modifiers(splitKeys((String) row[13]))
                                .unusualEffect((String) row[14])
                                .build());
        }

        @Cacheable(value = "statistics", key = "'valuableinventory:' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<InventoryValueResponse> getMostValuableInventory(String country, Pageable pageable) {
                String normalizedCountry = normalizeCountry(country);
                String sql = """
                                SELECT u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country,
                                        COALESCE(SUM(i.worth * l.quantity), 0) AS items_value,
                                        u.item_essence AS essence,
                                        COALESCE(SUM(i.worth * l.quantity), 0) + u.item_essence AS total_value
                                FROM users u
                                LEFT JOIN user_item_links l ON l.user_id = u.id
                                LEFT JOIN items i ON i.id = l.item_id AND i.tradeable = true
                                WHERE u.active = true AND u.banned = false
                                """;
                if (normalizedCountry != null)
                        sql += " AND LOWER(u.country) = LOWER(:country)";
                sql += " GROUP BY u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country, u.item_essence" +
                                " HAVING COALESCE(SUM(i.worth * l.quantity), 0) + u.item_essence > 0" +
                                " ORDER BY total_value DESC, u.name ASC";

                Map<String, Object> params = normalizedCountry != null ? Map.of("country", normalizedCountry) : Map.of();

                return executePagedNativeQuery(sql, params, pageable, row -> InventoryValueResponse.builder()
                                .userId(String.valueOf(((Number) row[0]).longValue()))
                                .userName((String) row[1])
                                .avatarUrl((String) row[2])
                                .cdnAvatarUrl((String) row[3])
                                .country((String) row[4])
                                .itemsValue((BigDecimal) row[5])
                                .essenceBalance((BigDecimal) row[6])
                                .totalValue((BigDecimal) row[7])
                                .build());
        }

        @Cacheable(value = "statistics", key = "'firsteditions:' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<FirstEditionsResponse> getFirstEditions(String country, Pageable pageable) {
                String normalizedCountry = normalizeCountry(country);
                String sql = """
                                SELECT u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country, COUNT(*) AS first_edition_count
                                FROM user_item_links l
                                JOIN items i ON i.id = l.item_id
                                JOIN users u ON u.id = l.user_id
                                WHERE l.serial_number = 1 AND i.tradeable = true
                                        AND u.active = true AND u.banned = false
                                """;
                if (normalizedCountry != null)
                        sql += " AND LOWER(u.country) = LOWER(:country)";
                sql += " GROUP BY u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country" +
                                " ORDER BY first_edition_count DESC, u.name ASC";

                Map<String, Object> params = normalizedCountry != null ? Map.of("country", normalizedCountry) : Map.of();

                return executePagedNativeQuery(sql, params, pageable, row -> FirstEditionsResponse.builder()
                                .userId(String.valueOf(((Number) row[0]).longValue()))
                                .userName((String) row[1])
                                .avatarUrl((String) row[2])
                                .cdnAvatarUrl((String) row[3])
                                .country((String) row[4])
                                .firstEditionCount(((Number) row[5]).longValue())
                                .build());
        }

        @Cacheable(value = "statistics", key = "'firsteditionholders:' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<FirstEditionHolderResponse> getFirstEditionHolders(String country, Pageable pageable) {
                String normalizedCountry = normalizeCountry(country);
                String sql = """
                                SELECT i.id, i.name, i.icon_url, i.rarity, t.key, l.id, l.serial_number,
                                        u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country
                                FROM user_item_links l
                                JOIN items i ON i.id = l.item_id
                                JOIN item_types t ON t.id = i.type_id
                                JOIN users u ON u.id = l.user_id
                                WHERE l.serial_number = 1 AND i.tradeable = true
                                        AND u.active = true AND u.banned = false
                                """;
                if (normalizedCountry != null)
                        sql += " AND LOWER(u.country) = LOWER(:country)";
                sql += " ORDER BY " + rarityRank("i.rarity") + " DESC, i.name ASC";

                Map<String, Object> params = normalizedCountry != null ? Map.of("country", normalizedCountry) : Map.of();

                return executePagedNativeQuery(sql, params, pageable, row -> FirstEditionHolderResponse.builder()
                                .itemId((UUID) row[0])
                                .itemName((String) row[1])
                                .iconUrl((String) row[2])
                                .rarity((String) row[3])
                                .typeKey((String) row[4])
                                .linkId((UUID) row[5])
                                .serialNumber(row[6] != null ? ((Number) row[6]).longValue() : null)
                                .userId(String.valueOf(((Number) row[7]).longValue()))
                                .userName((String) row[8])
                                .avatarUrl((String) row[9])
                                .cdnAvatarUrl((String) row[10])
                                .country((String) row[11])
                                .build());
        }

        @Cacheable(value = "statistics", key = "'completecollection:' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<CollectionCompletionResponse> getMostCompleteCollection(String country, Pageable pageable) {
                String normalizedCountry = normalizeCountry(country);
                String catalog = "(SELECT COUNT(*) FROM items ci WHERE ci.tradeable = true" +
                                " AND ci.active = true AND ci.visible = true AND ci.deprecated = false)";
                String sql = "SELECT u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country," +
                                " COUNT(DISTINCT i.id) AS owned_count," +
                                " " + catalog + " AS catalog_total," +
                                " ROUND(100.0 * COUNT(DISTINCT i.id) / NULLIF(" + catalog + ", 0), 2) AS completion_percent" +
                                " FROM user_item_links l" +
                                " JOIN items i ON i.id = l.item_id AND i.tradeable = true" +
                                " AND i.active = true AND i.visible = true AND i.deprecated = false" +
                                " JOIN users u ON u.id = l.user_id" +
                                " WHERE u.active = true AND u.banned = false";
                if (normalizedCountry != null)
                        sql += " AND LOWER(u.country) = LOWER(:country)";
                sql += " GROUP BY u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country" +
                                " ORDER BY owned_count DESC, u.name ASC";

                Map<String, Object> params = normalizedCountry != null ? Map.of("country", normalizedCountry) : Map.of();

                return executePagedNativeQuery(sql, params, pageable, row -> CollectionCompletionResponse.builder()
                                .userId(String.valueOf(((Number) row[0]).longValue()))
                                .userName((String) row[1])
                                .avatarUrl((String) row[2])
                                .cdnAvatarUrl((String) row[3])
                                .country((String) row[4])
                                .ownedCount(((Number) row[5]).longValue())
                                .catalogTotal(((Number) row[6]).longValue())
                                .completionPercent((BigDecimal) row[7])
                                .build());
        }

        @Cacheable(value = "statistics", key = "'itemscarcity:' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<ItemScarcityResponse> getItemScarcity(Pageable pageable) {
                String sql = """
                                SELECT i.id, i.name, i.icon_url, i.rarity, t.key, COUNT(DISTINCT u.id) AS owner_count,
                                        COALESCE(SUM(CASE WHEN u.id IS NOT NULL THEN l.quantity END), 0) AS instance_count
                                FROM items i
                                JOIN item_types t ON t.id = i.type_id
                                LEFT JOIN user_item_links l ON l.item_id = i.id
                                LEFT JOIN users u ON u.id = l.user_id AND u.active = true AND u.banned = false
                                WHERE i.tradeable = true AND i.active = true AND i.visible = true AND i.deprecated = false
                                GROUP BY i.id, i.name, i.icon_url, i.rarity, t.key
                                """;
                sql += " ORDER BY instance_count ASC, owner_count ASC, " + rarityRank("i.rarity") + " DESC, i.name ASC";

                return executePagedNativeQuery(sql, Map.of(), pageable, row -> ItemScarcityResponse.builder()
                                .itemId((UUID) row[0])
                                .itemName((String) row[1])
                                .iconUrl((String) row[2])
                                .rarity((String) row[3])
                                .typeKey((String) row[4])
                                .ownerCount(((Number) row[5]).longValue())
                                .instanceCount(((Number) row[6]).longValue())
                                .build());
        }

        public Page<ItemHolderResponse> getItemHolders(UUID itemId, List<String> modifiers, String search,
                    ItemHolderSort sort, Long viewerId, Pageable pageable) {
            List<String> modifierKeys = normalizeModifiers(modifiers);
            boolean hasViewer = viewerId != null;
            if (sort == ItemHolderSort.FOLLOWING && !hasViewer) {
                    throw new UnauthorizedException("Log in to sort holders by players you follow");
            }
            String followingSelect = hasViewer ? "BOOL_OR(ur.id IS NOT NULL)" : "false";
            String followingJoin = hasViewer
                            ? " LEFT JOIN user_relations ur ON ur.target_user_id = u.id AND ur.user_id = :viewerId"
                                            + " AND ur.type = 'follower' AND ur.active = true"
                            : "";

            String sql = "SELECT u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country,"
                            + " SUM(l.quantity) AS quantity, MIN(l.serial_number) AS lowest_serial,"
                            + " MAX(l.awarded_at) AS acquired_at,"
                            + " (SELECT STRING_AGG(DISTINCT im.key, ',') FROM user_item_links l2"
                            + " JOIN user_item_link_modifiers lm ON lm.user_item_link_id = l2.id"
                            + " JOIN item_modifiers im ON im.id = lm.modifier_id"
                            + " WHERE l2.item_id = :itemId AND l2.user_id = u.id) AS modifier_keys,"
                            + " MIN(ucs.ranking) AS ranking, " + followingSelect + " AS following"
                            + " FROM user_item_links l"
                            + " JOIN users u ON u.id = l.user_id AND u.active = true AND u.banned = false"
                            + " LEFT JOIN user_category_statistics ucs ON ucs.user_id = u.id AND ucs.active = true"
                            + " AND ucs.category_id = (SELECT id FROM categories WHERE code = 'overall' AND active = true LIMIT 1)"
                            + followingJoin
                            + " WHERE l.item_id = :itemId";
            if (!modifierKeys.isEmpty()) {
                    sql += " AND (SELECT COUNT(DISTINCT im.key) FROM user_item_link_modifiers lm"
                                    + " JOIN item_modifiers im ON im.id = lm.modifier_id"
                                    + " WHERE lm.user_item_link_id = l.id AND im.key IN (:modifiers)) = :modifierCount";
            }
            if (search != null && !search.isBlank()) {
                    sql += " AND LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%'))";
            }
            sql += " GROUP BY u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country ORDER BY " + holderOrderBy(sort);

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("itemId", itemId);
            if (!modifierKeys.isEmpty()) {
                    params.put("modifiers", modifierKeys);
                    params.put("modifierCount", (long) modifierKeys.size());
            }
            if (search != null && !search.isBlank()) {
                    params.put("search", search.trim());
            }
            if (hasViewer) {
                    params.put("viewerId", viewerId);
            }

            return executePagedNativeQuery(sql, params, pageable, row -> ItemHolderResponse.builder()
                            .userId(String.valueOf(((Number) row[0]).longValue()))
                            .userName((String) row[1])
                            .avatarUrl((String) row[2])
                            .cdnAvatarUrl((String) row[3])
                            .country((String) row[4])
                            .quantity(((Number) row[5]).longValue())
                            .lowestSerial(row[6] != null ? ((Number) row[6]).longValue() : null)
                            .acquiredAt(row[7] != null ? (Instant) row[7] : null)
                            .modifiers(splitKeys((String) row[8]))
                            .ranking(row[9] != null ? ((Number) row[9]).intValue() : null)
                            .following(row[10] != null && (Boolean) row[10])
                            .build());
    }

    private static String holderOrderBy(ItemHolderSort sort) {
            return switch (sort == null ? ItemHolderSort.RECENT : sort) {
                    case RANK -> "ranking ASC NULLS LAST, acquired_at DESC NULLS LAST, u.name ASC";
                    case FOLLOWING -> "following DESC, ranking ASC NULLS LAST, u.name ASC";
                    case RECENT -> "acquired_at DESC NULLS LAST, u.name ASC";
            };
    }

    private static List<String> normalizeModifiers(List<String> modifiers) {
            if (modifiers == null || modifiers.isEmpty()) {
                    return List.of();
            }
            return modifiers.stream()
                            .filter(m -> m != null && !m.isBlank())
                            .map(String::trim)
                            .distinct()
                            .toList();
    }

    @Cacheable(value = "statistics", key = "'biggesttraders:' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<BiggestTraderResponse> getBiggestTraders(String country, Pageable pageable) {
                String normalizedCountry = normalizeCountry(country);
                String sql = """
                                SELECT u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country,
                                        COUNT(DISTINCT parties.trade_id) AS trade_count,
                                        COALESCE(SUM(ti.quantity), 0) AS items_traded
                                FROM (
                                        SELECT from_user_id AS user_id, id AS trade_id FROM user_item_trades WHERE status = 'accepted'
                                        UNION ALL
                                        SELECT to_user_id AS user_id, id AS trade_id FROM user_item_trades WHERE status = 'accepted'
                                ) parties
                                JOIN users u ON u.id = parties.user_id
                                JOIN user_item_trade_items ti ON ti.trade_id = parties.trade_id
                                WHERE u.active = true AND u.banned = false
                                """;
                if (normalizedCountry != null)
                        sql += " AND LOWER(u.country) = LOWER(:country)";
                sql += " GROUP BY u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country" +
                                " ORDER BY trade_count DESC, items_traded DESC, u.name ASC";

                Map<String, Object> params = normalizedCountry != null ? Map.of("country", normalizedCountry) : Map.of();

                return executePagedNativeQuery(sql, params, pageable, row -> BiggestTraderResponse.builder()
                                .userId(String.valueOf(((Number) row[0]).longValue()))
                                .userName((String) row[1])
                                .avatarUrl((String) row[2])
                                .cdnAvatarUrl((String) row[3])
                                .country((String) row[4])
                                .tradeCount(((Number) row[5]).longValue())
                                .itemsTraded(((Number) row[6]).longValue())
                                .build());
        }

        @Cacheable(value = "statistics", key = "'essenceearned:' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
        public Page<EssenceEarnedResponse> getMostEssenceEarned(String country, Pageable pageable) {
                String normalizedCountry = normalizeCountry(country);
                String sql = """
                                SELECT u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country,
                                        COALESCE(SUM(d.essence_gained), 0) AS essence_earned
                                FROM user_item_disintegrations d
                                JOIN items i ON i.id = d.item_id
                                JOIN users u ON u.id = d.user_id
                                WHERE i.tradeable = true AND u.active = true AND u.banned = false
                                """;
                if (normalizedCountry != null)
                        sql += " AND LOWER(u.country) = LOWER(:country)";
                sql += " GROUP BY u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country" +
                                " HAVING COALESCE(SUM(d.essence_gained), 0) > 0" +
                                " ORDER BY essence_earned DESC, u.name ASC";

                Map<String, Object> params = normalizedCountry != null ? Map.of("country", normalizedCountry) : Map.of();

                return executePagedNativeQuery(sql, params, pageable, row -> EssenceEarnedResponse.builder()
                                .userId(String.valueOf(((Number) row[0]).longValue()))
                                .userName((String) row[1])
                                .avatarUrl((String) row[2])
                                .cdnAvatarUrl((String) row[3])
                                .country((String) row[4])
                                .essenceEarned((BigDecimal) row[5])
                                .build());
        }

        private static String rarityRank(String column) {
                return "CASE " + column + " WHEN 'mythic' THEN 6 WHEN 'legendary' THEN 5 WHEN 'epic' THEN 4" +
                                " WHEN 'rare' THEN 3 WHEN 'uncommon' THEN 2 WHEN 'common' THEN 1 ELSE 0 END";
        }

        private static List<String> splitKeys(String aggregated) {
                if (aggregated == null || aggregated.isBlank())
                        return List.of();
                return List.of(aggregated.split(","));
        }

        private static String normalizeCountry(String country) {
                if (country == null)
                        return null;
                String trimmed = country.trim();
                return trimmed.isEmpty() ? null : trimmed;
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
        private List<TimeSeriesPointResponse> executeTimeSeriesQuery(String sql, Instant since, String country) {
                Query query = entityManager.createNativeQuery(sql);
                query.setParameter("since", since);
                if (country != null && sql.contains(":country"))
                        query.setParameter("country", country);
                List<Object[]> rows = query.getResultList();
                return rows.stream()
                                .map(row -> TimeSeriesPointResponse.builder()
                                                .date((java.time.LocalDate) row[0])
                                                .value(((Number) row[1]).longValue())
                                                .build())
                                .toList();
        }

        @SuppressWarnings("unchecked")
        private List<DistributionEntryResponse> executeDistributionQuery(String sql, Map<String, Object> params) {
                Query query = entityManager.createNativeQuery(sql);
                params.forEach(query::setParameter);
                List<Object[]> rows = query.getResultList();
                return rows.stream()
                                .map(row -> DistributionEntryResponse.builder()
                                                .label((String) row[0])
                                                .count(((Number) row[1]).longValue())
                                                .build())
                                .toList();
        }
}
