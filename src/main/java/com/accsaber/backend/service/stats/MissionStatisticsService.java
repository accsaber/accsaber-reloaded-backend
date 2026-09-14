package com.accsaber.backend.service.stats;

import static com.accsaber.backend.service.stats.StatsQueryRunner.dbl;
import static com.accsaber.backend.service.stats.StatsQueryRunner.num;
import static com.accsaber.backend.service.stats.StatsQueryRunner.rate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.dto.response.statistics.DistributionEntryResponse;
import com.accsaber.backend.model.dto.response.statistics.MissionCalibrationResponse;
import com.accsaber.backend.model.dto.response.statistics.MissionCompletorResponse;
import com.accsaber.backend.model.dto.response.statistics.MissionXpResponse;
import com.accsaber.backend.model.dto.response.statistics.TimeSeriesPointResponse;
import com.accsaber.backend.model.entity.mission.MissionBand;
import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.MissionProgressAxis;
import com.accsaber.backend.model.entity.mission.MissionType;
import com.accsaber.backend.service.clan.ClanRefCache;
import com.accsaber.backend.util.TimeRangeUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissionStatisticsService {

    static final String TIER_CASE = "CASE"
            + " WHEN m.assigned_skill_threshold IS NULL THEN 'unknown'"
            + " WHEN m.assigned_skill_threshold < 400 THEN 'new'"
            + " WHEN m.assigned_skill_threshold < 600 THEN 'casual'"
            + " WHEN m.assigned_skill_threshold < 750 THEN 'moderate'"
            + " WHEN m.assigned_skill_threshold < 950 THEN 'strong'"
            + " WHEN m.assigned_skill_threshold < 1100 THEN 'top'"
            + " ELSE 'elite' END";

    private static final String COUNTABLE = "m.status <> 'voided'";

    private final StatsQueryRunner queryRunner;

    @Cacheable(value = "statistics", key = "'msn:calib:' + #filter.cacheKey() + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<MissionCalibrationResponse> getCalibration(MissionStatsFilter filter, Pageable pageable) {
        Map<String, Object> params = new LinkedHashMap<>();
        String sql = "SELECT t.id, t.code, t.name, t.type, m.pool, m.band,"
                + " c.id AS cat_id, c.name AS cat_name, " + TIER_CASE + " AS tier,"
                + " COUNT(*) FILTER (WHERE " + COUNTABLE + ") AS assigned,"
                + " COUNT(*) FILTER (WHERE m.status = 'completed') AS completed,"
                + " COUNT(*) FILTER (WHERE m.status = 'expired') AS expired,"
                + " COUNT(*) FILTER (WHERE m.status = 'active') AS still_open,"
                + " COUNT(*) FILTER (WHERE " + COUNTABLE
                + " AND (m.progress_count > 0 OR m.progress_ap > 0)) AS progressed,"
                + " COUNT(*) FILTER (WHERE m.status = 'completed'"
                + " AND (m.progress_count > 0 OR m.progress_ap > 0)) AS progressed_completed,"
                + " AVG(EXTRACT(EPOCH FROM (m.completed_at - m.assigned_at)) / 3600.0)"
                + " FILTER (WHERE m.status = 'completed') AS avg_hours,"
                + " AVG(m.xp_reward) FILTER (WHERE " + COUNTABLE + ") AS avg_xp,"
                + " COUNT(*) FILTER (WHERE m.item_awarded = true) AS items_awarded,"
                + " COUNT(DISTINCT m.user_id) AS players,"
                + " COUNT(DISTINCT m.user_id) FILTER (WHERE m.status = 'completed') AS players_completed,"
                + " COALESCE(SUM(m.xp_reward) FILTER (WHERE m.status = 'completed'), 0) AS xp_paid,"
                + " BOOL_OR(t.repeatable) AS repeatable"
                + " FROM user_missions m"
                + " JOIN mission_templates t ON t.id = m.template_id"
                + " LEFT JOIN categories c ON c.id = m.category_id"
                + " WHERE m.user_id IS NOT NULL";
        sql += filter.appendTo(params);
        sql += " GROUP BY t.id, t.code, t.name, t.type, m.pool, m.band, c.id, c.name, tier"
                + " HAVING COUNT(*) FILTER (WHERE " + COUNTABLE + ") >= :minAssigned"
                + " ORDER BY assigned DESC, t.code ASC, m.band ASC";
        params.put("minAssigned", (long) filter.minAssigned());

        return queryRunner.paged(sql, params, pageable, row -> {
            long assigned = num(row[9]);
            long completed = num(row[10]);
            long progressed = num(row[13]);
            long progressedCompleted = num(row[14]);
            MissionType type = MissionType.valueOf((String) row[3]);
            boolean tracksProgress = type.getAxis() != MissionProgressAxis.BINARY;
            return MissionCalibrationResponse.builder()
                    .templateId((UUID) row[0])
                    .templateCode((String) row[1])
                    .templateName((String) row[2])
                    .type(type)
                    .pool(MissionPool.valueOf((String) row[4]))
                    .band(MissionBand.valueOf((String) row[5]))
                    .categoryId((UUID) row[6])
                    .categoryName((String) row[7])
                    .tier((String) row[8])
                    .assigned(assigned)
                    .completed(completed)
                    .expired(num(row[11]))
                    .stillOpen(num(row[12]))
                    .completionRate(rate(completed, assigned))
                    .progressed(tracksProgress ? progressed : null)
                    .progressedCompletionRate(tracksProgress ? rate(progressedCompleted, progressed) : null)
                    .averageHoursToComplete(dbl(row[15]))
                    .averageXpReward(dbl(row[16]))
                    .itemsAwarded(num(row[17]))
                    .players(num(row[18]))
                    .playersCompleted(num(row[19]))
                    .playerCompletionRate(rate(num(row[19]), num(row[18])))
                    .xpPaid(num(row[20]))
                    .repeatable((Boolean) row[21])
                    .build();
        });
    }

    @Cacheable(value = "statistics", key = "'msn:tier:' + #templateId + ':' + #filter.cacheKey()")
    public List<MissionCalibrationResponse> getByTier(UUID templateId, MissionStatsFilter filter) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("templateId", templateId);
        String sql = "SELECT t.id, t.code, t.name, t.type, m.pool, " + TIER_CASE + " AS tier,"
                + " COUNT(*) FILTER (WHERE " + COUNTABLE + ") AS assigned,"
                + " COUNT(*) FILTER (WHERE m.status = 'completed') AS completed,"
                + " COUNT(*) FILTER (WHERE m.status = 'expired') AS expired,"
                + " COUNT(*) FILTER (WHERE m.status = 'active') AS still_open,"
                + " AVG(EXTRACT(EPOCH FROM (m.completed_at - m.assigned_at)) / 3600.0)"
                + " FILTER (WHERE m.status = 'completed') AS avg_hours,"
                + " AVG(m.xp_reward) FILTER (WHERE " + COUNTABLE + ") AS avg_xp,"
                + " MIN(COALESCE(m.assigned_skill_threshold, -1)) AS tier_floor,"
                + " COUNT(DISTINCT m.user_id) AS players,"
                + " COUNT(DISTINCT m.user_id) FILTER (WHERE m.status = 'completed') AS players_completed,"
                + " COALESCE(SUM(m.xp_reward) FILTER (WHERE m.status = 'completed'), 0) AS xp_paid,"
                + " BOOL_OR(t.repeatable) AS repeatable"
                + " FROM user_missions m"
                + " JOIN mission_templates t ON t.id = m.template_id"
                + " WHERE m.user_id IS NOT NULL AND m.template_id = :templateId";
        sql += filter.appendTo(params);
        sql += " GROUP BY t.id, t.code, t.name, t.type, m.pool, tier ORDER BY tier_floor";

        return queryRunner.list(sql, params, row -> {
            long assigned = num(row[6]);
            long completed = num(row[7]);
            return MissionCalibrationResponse.builder()
                    .templateId((UUID) row[0])
                    .templateCode((String) row[1])
                    .templateName((String) row[2])
                    .type(MissionType.valueOf((String) row[3]))
                    .pool(MissionPool.valueOf((String) row[4]))
                    .tier((String) row[5])
                    .assigned(assigned)
                    .completed(completed)
                    .expired(num(row[8]))
                    .stillOpen(num(row[9]))
                    .completionRate(rate(completed, assigned))
                    .averageHoursToComplete(dbl(row[10]))
                    .averageXpReward(dbl(row[11]))
                    .players(num(row[13]))
                    .playersCompleted(num(row[14]))
                    .playerCompletionRate(rate(num(row[14]), num(row[13])))
                    .xpPaid(num(row[15]))
                    .repeatable((Boolean) row[16])
                    .build();
        });
    }

    @Cacheable(value = "statistics", key = "'msn:xp:' + #filter.cacheKey() + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<MissionXpResponse> getXpPayouts(MissionStatsFilter filter, Pageable pageable) {
        Map<String, Object> params = new LinkedHashMap<>();
        String sql = "SELECT t.id, t.code, t.name, t.type, m.pool, m.band,"
                + " COUNT(*) AS completed,"
                + " COALESCE(SUM(m.xp_reward), 0) AS xp_paid,"
                + " AVG(m.xp_reward) AS avg_xp,"
                + " PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY m.xp_reward) AS median_xp,"
                + " PERCENTILE_CONT(0.9) WITHIN GROUP (ORDER BY m.xp_reward) AS p90_xp,"
                + " COUNT(*) FILTER (WHERE m.item_awarded = true) AS items_awarded,"
                + " SUM(COALESCE(SUM(m.xp_reward), 0)) OVER () AS xp_grand_total"
                + " FROM user_missions m"
                + " JOIN mission_templates t ON t.id = m.template_id"
                + " WHERE m.user_id IS NOT NULL AND m.status = 'completed'";
        sql += filter.appendTo(params);
        sql += " GROUP BY t.id, t.code, t.name, t.type, m.pool, m.band ORDER BY xp_paid DESC, t.code ASC";

        return queryRunner.paged(sql, params, pageable, row -> {
            long xpPaid = num(row[7]);
            long grandTotal = num(row[12]);
            return MissionXpResponse.builder()
                    .templateId((UUID) row[0])
                    .templateCode((String) row[1])
                    .templateName((String) row[2])
                    .type(MissionType.valueOf((String) row[3]))
                    .pool(MissionPool.valueOf((String) row[4]))
                    .band(MissionBand.valueOf((String) row[5]))
                    .completed(num(row[6]))
                    .xpPaid(xpPaid)
                    .averageXp(dbl(row[8]))
                    .medianXp(dbl(row[9]))
                    .p90Xp(dbl(row[10]))
                    .itemsAwarded(num(row[11]))
                    .shareOfMissionXp(rate(xpPaid, grandTotal))
                    .build();
        });
    }

    @Cacheable(value = "statistics", key = "'msn:completionrate:' + #amount + ':' + #unit + ':' + #filter.cacheKey()")
    public List<TimeSeriesPointResponse> getCompletionRateOverTime(int amount, String unit,
            MissionStatsFilter filter) {
        Instant since = TimeRangeUtil.computeSince(amount, unit);
        String bucket = TimeRangeUtil.granularity(since);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("since", since);
        String sql = "SELECT DATE_TRUNC('" + bucket + "', m.assigned_at)::date AS bucket,"
                + " ROUND(100.0 * COUNT(*) FILTER (WHERE m.status = 'completed')"
                + " / NULLIF(COUNT(*) FILTER (WHERE " + COUNTABLE + "), 0)) AS pct"
                + " FROM user_missions m"
                + " JOIN mission_templates t ON t.id = m.template_id"
                + " WHERE m.user_id IS NOT NULL AND m.assigned_at >= :since";
        sql += filter.appendTo(params);
        sql += " GROUP BY bucket HAVING COUNT(*) FILTER (WHERE " + COUNTABLE + ") > 0 ORDER BY bucket";
        return queryRunner.timeSeries(sql, params);
    }

    @Cacheable(value = "statistics", key = "'msn:perday:' + #amount + ':' + #unit + ':' + #filter.cacheKey()")
    public List<TimeSeriesPointResponse> getCompletionsPerDay(int amount, String unit,
            MissionStatsFilter filter) {
        Instant since = TimeRangeUtil.computeSince(amount, unit);
        String bucket = TimeRangeUtil.granularity(since);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("since", since);
        String sql = "SELECT DATE_TRUNC('" + bucket + "', m.completed_at)::date AS bucket, COUNT(*) AS cnt"
                + " FROM user_missions m"
                + " JOIN mission_templates t ON t.id = m.template_id"
                + " WHERE m.user_id IS NOT NULL AND m.status = 'completed' AND m.completed_at >= :since";
        sql += filter.appendTo(params);
        sql += " GROUP BY bucket ORDER BY bucket";
        return queryRunner.timeSeries(sql, params);
    }

    @Cacheable(value = "statistics", key = "'msn:bytype:' + #filter.cacheKey()")
    public List<DistributionEntryResponse> getCompletionsByType(MissionStatsFilter filter) {
        Map<String, Object> params = new LinkedHashMap<>();
        String sql = "SELECT t.type AS label, COUNT(*) AS cnt"
                + " FROM user_missions m"
                + " JOIN mission_templates t ON t.id = m.template_id"
                + " WHERE m.user_id IS NOT NULL AND m.status = 'completed'";
        sql += filter.appendTo(params);
        sql += " GROUP BY t.type ORDER BY cnt DESC";
        return queryRunner.distribution(sql, params);
    }

    @Cacheable(value = "statistics", key = "'msn:mostcompleted:' + #filter.cacheKey() + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<MissionCompletorResponse> getMostCompleted(MissionStatsFilter filter, Pageable pageable) {
        Map<String, Object> params = new LinkedHashMap<>();
        String sql = "SELECT u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country,"
                + " COUNT(*) AS completed, COALESCE(SUM(m.xp_reward), 0) AS xp_earned"
                + " FROM user_missions m"
                + " JOIN mission_templates t ON t.id = m.template_id"
                + " JOIN users u ON u.id = m.user_id"
                + " WHERE m.status = 'completed' AND u.active = true AND u.banned = false";
        sql += filter.appendTo(params);
        sql += " GROUP BY u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country"
                + " ORDER BY completed DESC, xp_earned DESC, u.name ASC";
        return queryRunner.paged(sql, params, pageable, MissionStatisticsService::mapCompletor);
    }

    @Cacheable(value = "statistics", key = "'msn:mostxp:' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<MissionCompletorResponse> getMostMissionXp(String country, Pageable pageable) {
        String normalizedCountry = StatsQueryRunner.normalizeCountry(country);
        Map<String, Object> params = new LinkedHashMap<>();
        String sql = "SELECT u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country,"
                + " (SELECT COUNT(*) FROM user_missions m"
                + " WHERE m.user_id = u.id AND m.status = 'completed') AS completed,"
                + " u.mission_xp AS xp_earned"
                + " FROM users u"
                + " WHERE u.active = true AND u.banned = false AND u.mission_xp > 0";
        if (normalizedCountry != null) {
            sql += " AND LOWER(u.country) = LOWER(:country)";
            params.put("country", normalizedCountry);
        }
        sql += " ORDER BY xp_earned DESC, u.name ASC";
        return queryRunner.paged(sql, params, pageable, MissionStatisticsService::mapCompletor);
    }

    private static MissionCompletorResponse mapCompletor(Object[] row) {
        return MissionCompletorResponse.builder()
                .userId(String.valueOf(((Number) row[0]).longValue()))
                .clan(ClanRefCache.forUser(((Number) row[0]).longValue()))
                .userName((String) row[1])
                .avatarUrl((String) row[2])
                .cdnAvatarUrl((String) row[3])
                .country((String) row[4])
                .missionsCompleted(num(row[5]))
                .missionXp(((Number) row[6]).doubleValue())
                .build();
    }

}
