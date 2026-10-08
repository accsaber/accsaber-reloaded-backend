package com.accsaber.backend.service.stats;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.statistics.DistributionEntryResponse;
import com.accsaber.backend.model.dto.response.statistics.MissionShortfallResponse;
import com.accsaber.backend.model.entity.mission.MissionBand;
import com.accsaber.backend.model.entity.mission.MissionTemplate;
import com.accsaber.backend.model.entity.mission.MissionType;
import com.accsaber.backend.repository.mission.MissionTemplateRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissionShortfallService {

    private static final int BUCKETS = 10;

    private final StatsQueryRunner queryRunner;
    private final MissionTemplateRepository templateRepository;

    @Cacheable(value = "statistics", key = "'msn:shortfall:' + #templateId + ':' + #filter.cacheKey()")
    public List<MissionShortfallResponse> getShortfall(UUID templateId, MissionStatsFilter filter) {
        MissionTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Mission template", templateId));

        String reached = reachedExpression(template.getType());
        if (reached == null) {
            return List.of();
        }

        String from = fromClause(template.getType(), reached);
        Map<MissionBand, long[]> histograms = loadHistograms(templateId, filter, from);

        Map<String, Object> params = baseParams(templateId);
        String sql = "SELECT m.band, COUNT(*) AS failed, COUNT(r.reached) AS measured,"
                + " PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY r.reached) AS median_reached"
                + from + whereClause(filter, params)
                + " GROUP BY m.band ORDER BY m.band";

        return queryRunner.list(sql, params, row -> {
            MissionBand band = MissionBand.valueOf((String) row[0]);
            return MissionShortfallResponse.builder()
                    .band(band)
                    .axis(template.getType().getAxis())
                    .failed(((Number) row[1]).longValue())
                    .measured(((Number) row[2]).longValue())
                    .medianReachedFraction(row[3] == null ? null : ((Number) row[3]).doubleValue())
                    .buckets(toBuckets(histograms.get(band)))
                    .build();
        });
    }

    private Map<MissionBand, long[]> loadHistograms(UUID templateId, MissionStatsFilter filter, String from) {
        Map<String, Object> params = baseParams(templateId);
        String sql = "SELECT m.band,"
                + " LEAST(" + BUCKETS + " - 1, GREATEST(0, FLOOR(r.reached * " + BUCKETS + ")))::int AS bucket,"
                + " COUNT(*) AS in_bucket"
                + from + whereClause(filter, params)
                + " AND r.reached IS NOT NULL GROUP BY m.band, bucket";

        Map<MissionBand, long[]> histograms = new EnumMap<>(MissionBand.class);
        queryRunner.list(sql, params, row -> {
            MissionBand band = MissionBand.valueOf((String) row[0]);
            histograms.computeIfAbsent(band, b -> new long[BUCKETS])[((Number) row[1]).intValue()] = ((Number) row[2])
                    .longValue();
            return band;
        });
        return histograms;
    }

    private Map<String, Object> baseParams(UUID templateId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("templateId", templateId);
        return params;
    }

    private String whereClause(MissionStatsFilter filter, Map<String, Object> params) {
        return " WHERE m.user_id IS NOT NULL AND m.status = 'expired' AND m.template_id = :templateId"
                + filter.appendTo(params);
    }

    private String fromClause(MissionType type, String reached) {
        return " FROM user_missions m"
                + " JOIN mission_templates t ON t.id = m.template_id"
                + joinsFor(type)
                + " CROSS JOIN LATERAL (SELECT (" + reached + ") AS reached) r";
    }

    private List<DistributionEntryResponse> toBuckets(long[] counts) {
        List<DistributionEntryResponse> buckets = new ArrayList<>(BUCKETS);
        int width = 100 / BUCKETS;
        for (int i = 0; i < BUCKETS; i++) {
            String label = i == BUCKETS - 1
                    ? (i * width) + "%+"
                    : (i * width) + "-" + ((i + 1) * width) + "%";
            buckets.add(DistributionEntryResponse.builder()
                    .label(label)
                    .count(counts == null ? 0L : counts[i])
                    .build());
        }
        return buckets;
    }

    private String joinsFor(MissionType type) {
        return switch (type) {
            case ACC_ON_MAP -> " JOIN map_difficulties d ON d.id = m.target_map_difficulty_id"
                    + bestJoin("s.score::double precision / NULLIF(d.max_score, 0)");
            case AP_ON_MAP, SNIPE_PLAYER_ON_MAP -> bestJoin("s.ap");
            case STREAK_ON_MAP -> bestJoin("s.streak_115::double precision");
            default -> "";
        };
    }

    private String bestJoin(String metric) {
        return " LEFT JOIN LATERAL ("
                + " SELECT MAX(" + metric + ") AS best FROM scores s"
                + " WHERE s.user_id = m.user_id AND s.map_difficulty_id = m.target_map_difficulty_id"
                + " AND s.active = true) b ON true";
    }

    private String reachedExpression(MissionType type) {
        return switch (type) {
            case PLAY_N_MAPS, PB_ABOVE_THRESHOLD, STREAK_N_IN_CATEGORY, STREAK_SUM_N, SCORES_N,
                    SNIPE_RIVAL_ANY_MAP, BATCH_PLAY_N, PB_RANKED_BEFORE_N, CAMPAIGN_COMPLETE_N,
                    MISSIONS_COMPLETE_N ->
                "m.progress_count::double precision / NULLIF(m.target_count, 0)";
            case XP_IN_WINDOW -> "m.progress_count::double precision / NULLIF(m.target_xp, 0)";
            case AP_GAIN_OVERALL -> "m.progress_ap / NULLIF(m.target_ap, 0)";
            case ACC_ON_MAP -> "b.best / NULLIF(m.target_acc, 0)";
            case AP_ON_MAP, SNIPE_PLAYER_ON_MAP -> "b.best / NULLIF(m.target_ap, 0)";
            case STREAK_ON_MAP -> "b.best / NULLIF(m.target_streak, 0)";
            case PB_SPECIFIC_MAP, COMEBACK_PB -> null;
        };
    }
}
