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

import com.accsaber.backend.model.dto.response.statistics.CampaignCompletorResponse;
import com.accsaber.backend.model.dto.response.statistics.CampaignCreatorResponse;
import com.accsaber.backend.model.dto.response.statistics.CampaignFunnelResponse;
import com.accsaber.backend.model.dto.response.statistics.CampaignNodeDifficultyResponse;
import com.accsaber.backend.model.dto.response.statistics.TimeSeriesPointResponse;
import com.accsaber.backend.model.entity.campaign.CampaignRequirementType;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.service.clan.ClanRefCache;
import com.accsaber.backend.util.TimeRangeUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignStatisticsService {

    private final StatsQueryRunner queryRunner;

    @Cacheable(value = "statistics", key = "'cmp:funnel:' + #filter.cacheKey() + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<CampaignFunnelResponse> getFunnel(CampaignStatsFilter filter, Pageable pageable) {
        Map<String, Object> params = new LinkedHashMap<>();
        String participantJoin = " LEFT JOIN user_campaigns uc ON uc.campaign_id = c.id AND uc.active = true"
                + filter.appendParticipantCountry(params);
        String sql = "SELECT c.id, c.name, c.slug, c.icon_url, c.status, c.official, c.loved,"
                + " COUNT(uc.id) AS participants,"
                + " COUNT(*) FILTER (WHERE uc.status = 'in_progress') AS in_progress,"
                + " COUNT(*) FILTER (WHERE uc.status = 'completed') AS completed,"
                + " COUNT(*) FILTER (WHERE uc.status = 'abandoned') AS abandoned,"
                + " PERCENTILE_CONT(0.5) WITHIN GROUP ("
                + " ORDER BY EXTRACT(EPOCH FROM (uc.completed_at - uc.started_at)) / 86400.0) AS median_days,"
                + " (SELECT COUNT(*) FROM campaign_difficulties cd"
                + " WHERE cd.campaign_id = c.id AND cd.active = true) AS node_count"
                + " FROM campaigns c"
                + participantJoin
                + " WHERE c.active = true AND c.status <> 'draft'"
                + filter.appendCampaignPredicate(params)
                + " GROUP BY c.id, c.name, c.slug, c.icon_url, c.status, c.official, c.loved"
                + " HAVING COUNT(uc.id) >= :minParticipants"
                + " ORDER BY participants DESC, c.name ASC";
        params.put("minParticipants", (long) filter.minParticipants());

        return queryRunner.paged(sql, params, pageable, row -> {
            long participants = num(row[7]);
            long completed = num(row[9]);
            long abandoned = num(row[10]);
            return CampaignFunnelResponse.builder()
                    .campaignId((UUID) row[0])
                    .name((String) row[1])
                    .slug((String) row[2])
                    .iconUrl((String) row[3])
                    .status((String) row[4])
                    .official((Boolean) row[5])
                    .loved((Boolean) row[6])
                    .participants(participants)
                    .inProgress(num(row[8]))
                    .completed(completed)
                    .abandoned(abandoned)
                    .completionRate(rate(completed, participants))
                    .abandonRate(rate(abandoned, participants))
                    .medianDaysToComplete(dbl(row[11]))
                    .nodeCount(num(row[12]))
                    .build();
        });
    }

    @Cacheable(value = "statistics", key = "'cmp:nodes:' + #campaignId + ':' + #country")
    public List<CampaignNodeDifficultyResponse> getNodeDifficulty(UUID campaignId, String country) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("campaignId", campaignId);
        String countryClause = StatsQueryRunner.countryExists("uc.user_id", country, params);

        String sql = "WITH participants AS ("
                + " SELECT uc.user_id FROM user_campaigns uc"
                + " WHERE uc.campaign_id = :campaignId AND uc.active = true"
                + " AND uc.status IN ('in_progress', 'completed')" + countryClause
                + "), clears AS ("
                + " SELECT ucs.campaign_difficulty_id, ucs.user_id, MIN(ucs.submitted_at) AS cleared_at"
                + " FROM user_campaign_scores ucs"
                + " WHERE ucs.campaign_id = :campaignId AND ucs.active = true"
                + " AND ucs.user_id IN (SELECT user_id FROM participants)"
                + " GROUP BY ucs.campaign_difficulty_id, ucs.user_id"
                + ")"
                + " SELECT cd.id, md.id, m.song_name, m.song_subname, m.song_author, m.map_author,"
                + " m.cover_url, m.cdn_cover_url, md.difficulty, cd.barrier, cd.terminal,"
                + " cd.requirement_type, cd.requirement_value, cd.xp,"
                + " (SELECT COUNT(*) FROM participants p WHERE NOT EXISTS ("
                + "   SELECT 1 FROM campaign_difficulty_paths pth"
                + "   WHERE pth.campaign_difficulty_id = cd.id AND pth.active = true"
                + "     AND NOT EXISTS (SELECT 1 FROM clears cl"
                + "        WHERE cl.campaign_difficulty_id = pth.comes_from_campaign_difficulty_id"
                + "          AND cl.user_id = p.user_id))) AS unlocked,"
                + " (SELECT COUNT(*) FROM clears cl WHERE cl.campaign_difficulty_id = cd.id) AS cleared,"
                + " (SELECT PERCENTILE_CONT(0.5) WITHIN GROUP ("
                + "   ORDER BY EXTRACT(EPOCH FROM (cl.cleared_at - uc.started_at)) / 86400.0)"
                + "  FROM clears cl JOIN user_campaigns uc"
                + "    ON uc.user_id = cl.user_id AND uc.campaign_id = :campaignId AND uc.active = true"
                + "  WHERE cl.campaign_difficulty_id = cd.id) AS median_days"
                + " FROM campaign_difficulties cd"
                + " JOIN map_difficulties md ON md.id = cd.map_difficulty_id"
                + " JOIN maps m ON m.id = md.map_id"
                + " WHERE cd.campaign_id = :campaignId AND cd.active = true"
                + " ORDER BY cleared ASC, cd.position_y, cd.position_x";

        return queryRunner.list(sql, params, row -> {
            long unlocked = num(row[14]);
            long cleared = num(row[15]);
            return CampaignNodeDifficultyResponse.builder()
                    .campaignDifficultyId((UUID) row[0])
                    .mapDifficultyId((UUID) row[1])
                    .songName((String) row[2])
                    .songSubName((String) row[3])
                    .songAuthor((String) row[4])
                    .mapAuthor((String) row[5])
                    .coverUrl((String) row[6])
                    .cdnCoverUrl((String) row[7])
                    .difficulty(Difficulty.fromDbValue((String) row[8]))
                    .barrier((Boolean) row[9])
                    .terminal((Boolean) row[10])
                    .requirementType(row[11] == null ? null : CampaignRequirementType.valueOf((String) row[11]))
                    .requirementValue(dbl(row[12]))
                    .xp(row[13] == null ? 0.0 : ((Number) row[13]).doubleValue())
                    .unlocked(unlocked)
                    .cleared(cleared)
                    .clearRate(rate(cleared, unlocked))
                    .medianDaysToClear(dbl(row[16]))
                    .build();
        });
    }

    @Cacheable(value = "statistics", key = "'cmp:startsperday:' + #amount + ':' + #unit + ':' + #filter.cacheKey()")
    public List<TimeSeriesPointResponse> getStartsPerDay(int amount, String unit, CampaignStatsFilter filter) {
        return campaignTimeSeries(amount, unit, "uc.started_at", null, filter);
    }

    @Cacheable(value = "statistics", key = "'cmp:completionsperday:' + #amount + ':' + #unit + ':' + #filter.cacheKey()")
    public List<TimeSeriesPointResponse> getCompletionsPerDay(int amount, String unit, CampaignStatsFilter filter) {
        return campaignTimeSeries(amount, unit, "uc.completed_at", "uc.status = 'completed'", filter);
    }

    private List<TimeSeriesPointResponse> campaignTimeSeries(int amount, String unit, String column,
            String extraCondition, CampaignStatsFilter filter) {
        Instant since = TimeRangeUtil.computeSince(amount, unit);
        String bucket = TimeRangeUtil.granularity(since);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("since", since);
        String sql = "SELECT DATE_TRUNC('" + bucket + "', " + column + ")::date AS bucket, COUNT(*) AS cnt"
                + " FROM user_campaigns uc"
                + " JOIN campaigns c ON c.id = uc.campaign_id AND c.active = true"
                + " WHERE uc.active = true AND " + column + " >= :since"
                + (extraCondition == null ? "" : " AND " + extraCondition)
                + filter.appendCampaignPredicate(params)
                + filter.appendParticipantCountry(params)
                + " GROUP BY bucket ORDER BY bucket";
        return queryRunner.timeSeries(sql, params);
    }

    @Cacheable(value = "statistics", key = "'cmp:mostcompleted:' + #filter.cacheKey() + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<CampaignCompletorResponse> getMostCompleted(CampaignStatsFilter filter, Pageable pageable) {
        Map<String, Object> params = new LinkedHashMap<>();
        String sql = "SELECT u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country,"
                + " COUNT(*) FILTER (WHERE uc.status = 'completed') AS completed,"
                + " COUNT(*) FILTER (WHERE uc.status = 'in_progress') AS in_progress,"
                + " (SELECT COUNT(*) FROM user_campaign_scores ucs"
                + "   WHERE ucs.user_id = u.id AND ucs.active = true) AS nodes_cleared,"
                + " u.campaign_xp"
                + " FROM user_campaigns uc"
                + " JOIN users u ON u.id = uc.user_id"
                + " JOIN campaigns c ON c.id = uc.campaign_id AND c.active = true"
                + " WHERE uc.active = true AND u.active = true AND u.banned = false"
                + filter.appendCampaignPredicate(params)
                + filter.appendParticipantCountry(params)
                + " GROUP BY u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country, u.campaign_xp"
                + " HAVING COUNT(*) FILTER (WHERE uc.status = 'completed') > 0"
                + " ORDER BY completed DESC, nodes_cleared DESC, u.name ASC";

        return queryRunner.paged(sql, params, pageable, row -> CampaignCompletorResponse.builder()
                .userId(String.valueOf(((Number) row[0]).longValue()))
                .clan(ClanRefCache.forUser(((Number) row[0]).longValue()))
                .userName((String) row[1])
                .avatarUrl((String) row[2])
                .cdnAvatarUrl((String) row[3])
                .country((String) row[4])
                .completed(num(row[5]))
                .inProgress(num(row[6]))
                .nodesCleared(num(row[7]))
                .campaignXp(row[8] == null ? 0.0 : ((Number) row[8]).doubleValue())
                .build());
    }

    @Cacheable(value = "statistics", key = "'cmp:creators:' + #filter.cacheKey() + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<CampaignCreatorResponse> getTopCreators(CampaignStatsFilter filter, Pageable pageable) {
        Map<String, Object> params = new LinkedHashMap<>();
        String normalizedCountry = StatsQueryRunner.normalizeCountry(filter.country());
        String sql = "SELECT u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country,"
                + " COUNT(DISTINCT c.id) AS campaigns,"
                + " COUNT(DISTINCT c.id) FILTER (WHERE c.status = 'curated') AS curated_campaigns,"
                + " COUNT(uc.id) AS participants,"
                + " COUNT(*) FILTER (WHERE uc.status = 'completed') AS completions"
                + " FROM campaigns c"
                + " JOIN users u ON u.id = c.creator_id"
                + " LEFT JOIN user_campaigns uc ON uc.campaign_id = c.id AND uc.active = true"
                + " WHERE c.active = true AND c.status <> 'draft' AND c.official = false"
                + " AND u.active = true AND u.banned = false"
                + filter.appendCampaignPredicate(params);
        if (normalizedCountry != null) {
            sql += " AND LOWER(u.country) = LOWER(:creatorCountry)";
            params.put("creatorCountry", normalizedCountry);
        }
        sql += " GROUP BY u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country"
                + " ORDER BY participants DESC, completions DESC, u.name ASC";

        return queryRunner.paged(sql, params, pageable, row -> {
            long participants = num(row[7]);
            long completions = num(row[8]);
            return CampaignCreatorResponse.builder()
                    .userId(String.valueOf(((Number) row[0]).longValue()))
                    .clan(ClanRefCache.forUser(((Number) row[0]).longValue()))
                    .userName((String) row[1])
                    .avatarUrl((String) row[2])
                    .cdnAvatarUrl((String) row[3])
                    .country((String) row[4])
                    .campaigns(num(row[5]))
                    .curatedCampaigns(num(row[6]))
                    .participants(participants)
                    .completions(completions)
                    .completionRate(rate(completions, participants))
                    .build();
        });
    }

}
