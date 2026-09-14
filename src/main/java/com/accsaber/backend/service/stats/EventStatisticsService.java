package com.accsaber.backend.service.stats;

import static com.accsaber.backend.service.stats.StatsQueryRunner.dbl;
import static com.accsaber.backend.service.stats.StatsQueryRunner.num;
import static com.accsaber.backend.service.stats.StatsQueryRunner.rate;

import java.time.Duration;
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

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.statistics.EventMissionLeaderboardResponse;
import com.accsaber.backend.model.dto.response.statistics.EventParticipationResponse;
import com.accsaber.backend.model.dto.response.statistics.EventSummaryResponse;
import com.accsaber.backend.model.dto.response.statistics.EventWeekStatsResponse;
import com.accsaber.backend.model.dto.response.statistics.MissionCalibrationResponse;
import com.accsaber.backend.model.entity.mission.Event;
import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.MissionType;
import com.accsaber.backend.repository.mission.EventRepository;
import com.accsaber.backend.service.clan.ClanRefCache;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventStatisticsService {

    private static final String TEMPLATE_WEEK = "LEAST("
            + " GREATEST(1, FLOOR(EXTRACT(EPOCH FROM"
            + "   (COALESCE(t.unlocks_at, e.starts_at) - e.starts_at)) / 604800)::int + 1),"
            + " GREATEST(1, FLOOR(EXTRACT(EPOCH FROM (e.ends_at - e.starts_at)) / 604800)::int))";

    private static final String PARTICIPANT_COUNTRY = " AND LOWER(u.country) = LOWER(:country)";

    private static final String PER_PLAYER = "WITH per_player AS ("
            + " SELECT m.template_id, m.user_id,"
            + " COUNT(*) FILTER (WHERE m.status = 'completed') AS completions,"
            + " COUNT(*) FILTER (WHERE m.status = 'expired') AS expirations,"
            + " COUNT(*) FILTER (WHERE m.status = 'active') AS still_open,"
            + " COALESCE(SUM(m.xp_reward) FILTER (WHERE m.status = 'completed'), 0) AS xp_paid,"
            + " COUNT(*) FILTER (WHERE m.item_awarded = true) AS items_awarded"
            + " FROM user_missions m"
            + " JOIN mission_templates mt ON mt.id = m.template_id"
            + " JOIN users u ON u.id = m.user_id AND u.active = true AND u.banned = false"
            + " WHERE mt.event_id = :eventId AND m.status <> 'voided'";

    private static final String PER_PLAYER_TAIL = " GROUP BY m.template_id, m.user_id)";

    private static final String PLAYER_AGGREGATES = " COUNT(pp.user_id) AS players,"
            + " COUNT(pp.user_id) FILTER (WHERE pp.completions > 0) AS players_completed,"
            + " COUNT(pp.user_id) FILTER (WHERE pp.completions = 0 AND pp.expirations > 0) AS players_expired,"
            + " COUNT(pp.user_id) FILTER (WHERE pp.completions = 0 AND pp.expirations = 0"
            + " AND pp.still_open > 0) AS players_open,"
            + " COALESCE(SUM(pp.completions), 0) AS completions,"
            + " COALESCE(SUM(pp.xp_paid), 0) AS xp_paid,"
            + " COALESCE(SUM(pp.items_awarded), 0) AS items_awarded";

    private final StatsQueryRunner queryRunner;
    private final EventRepository eventRepository;

    @Cacheable(value = "statistics", key = "'evt:summary:' + #eventId + ':' + #week + ':' + #country")
    public EventSummaryResponse getSummary(UUID eventId, Integer week, String country) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId));

        String normalizedCountry = StatsQueryRunner.normalizeCountry(country);
        Object[] profile = loadProfileTotals(eventId, normalizedCountry);
        List<EventWeekStatsResponse> weeks = loadWeeks(eventId, normalizedCountry);
        List<MissionCalibrationResponse> missions = loadMissions(eventId, week, normalizedCountry);

        long participants = num(profile[0]);
        long finishers = num(profile[1]);
        long bonusXp = num(profile[3]);

        long assigned = weeks.stream().mapToLong(EventWeekStatsResponse::getMissionsAssigned).sum();
        long completed = weeks.stream().mapToLong(EventWeekStatsResponse::getMissionsCompleted).sum();
        long expired = weeks.stream().mapToLong(EventWeekStatsResponse::getMissionsExpired).sum();
        long open = weeks.stream().mapToLong(EventWeekStatsResponse::getMissionsOpen).sum();
        long missionXp = weeks.stream().mapToLong(EventWeekStatsResponse::getXpPaid).sum();

        return EventSummaryResponse.builder()
                .eventId(event.getId())
                .title(event.getTitle())
                .slug(event.getSlug())
                .startsAt(event.getStartsAt())
                .endsAt(event.getEndsAt())
                .daysRan(Duration.between(event.getStartsAt(), event.getEndsAt()).toDays())
                .totalWeeks(event.totalWeeks())
                .week(week)
                .participants(participants)
                .finishers(finishers)
                .finishRate(rate(finishers, participants))
                .averageMissionsCompleted(participants <= 0 ? null : (double) completed / participants)
                .medianMissionsCompleted(dbl(profile[2]))
                .bonusXpPaid(bonusXp)
                .missionXpPaid(missionXp)
                .totalXpPaid(bonusXp + missionXp)
                .missionsAssigned(assigned)
                .missionsCompleted(completed)
                .missionsExpired(expired)
                .missionsOpen(open)
                .missionCompletionRate(rate(completed, assigned))
                .weeks(weeks)
                .missions(missions)
                .build();
    }

    private Object[] loadProfileTotals(UUID eventId, String country) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("eventId", eventId);
        String sql = "SELECT COUNT(*) AS participants,"
                + " COUNT(*) FILTER (WHERE p.completed_at IS NOT NULL) AS finishers,"
                + " PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY p.missions_completed) AS median_missions,"
                + " COALESCE(SUM(p.bonus_xp) FILTER (WHERE p.bonus_awarded_at IS NOT NULL), 0) AS bonus_xp"
                + " FROM user_event_profiles p"
                + " JOIN users u ON u.id = p.user_id AND u.active = true AND u.banned = false"
                + " WHERE p.event_id = :eventId" + countryFilter(country, params);
        return queryRunner.list(sql, params, row -> row).stream().findFirst()
                .orElse(new Object[] { 0L, 0L, null, 0L });
    }

    private List<EventWeekStatsResponse> loadWeeks(UUID eventId, String country) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("eventId", eventId);
        Map<Integer, long[]> participantsByWeek = loadParticipantsByWeek(eventId, country);
        String sql = PER_PLAYER + countryFilter(country, params) + PER_PLAYER_TAIL
                + " SELECT " + TEMPLATE_WEEK + " AS week," + PLAYER_AGGREGATES
                + " FROM mission_templates t"
                + " JOIN events e ON e.id = t.event_id"
                + " LEFT JOIN per_player pp ON pp.template_id = t.id"
                + " WHERE t.event_id = :eventId"
                + " GROUP BY week ORDER BY week";

        return queryRunner.list(sql, params, row -> {
            int week = ((Number) row[0]).intValue();
            long players = num(row[1]);
            long completed = num(row[2]);
            long[] counts = participantsByWeek.getOrDefault(week, new long[] { 0L, 0L });
            return EventWeekStatsResponse.builder()
                    .week(week)
                    .missionsAssigned(players)
                    .missionsCompleted(completed)
                    .missionsExpired(num(row[3]))
                    .missionsOpen(num(row[4]))
                    .completionRate(rate(completed, players))
                    .xpPaid(num(row[6]))
                    .participantsReached(counts[0])
                    .participantsStoppedHere(counts[1])
                    .build();
        });
    }

    private Map<Integer, long[]> loadParticipantsByWeek(UUID eventId, String country) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("eventId", eventId);
        String sql = "SELECT p.unlocked_week, COUNT(*) AS stopped_here,"
                + " SUM(COUNT(*)) OVER (ORDER BY p.unlocked_week DESC) AS reached"
                + " FROM user_event_profiles p"
                + " JOIN users u ON u.id = p.user_id AND u.active = true AND u.banned = false"
                + " WHERE p.event_id = :eventId" + countryFilter(country, params)
                + " GROUP BY p.unlocked_week";

        Map<Integer, long[]> byWeek = new LinkedHashMap<>();
        queryRunner.list(sql, params,
                row -> byWeek.put(((Number) row[0]).intValue(), new long[] { num(row[2]), num(row[1]) }));
        return byWeek;
    }

    private List<MissionCalibrationResponse> loadMissions(UUID eventId, Integer week, String country) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("eventId", eventId);
        String sql = PER_PLAYER + countryFilter(country, params) + PER_PLAYER_TAIL
                + " SELECT t.id, t.code, t.name, t.type, t.pool, t.repeatable, "
                + TEMPLATE_WEEK + " AS week,"
                + PLAYER_AGGREGATES + ","
                + " PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY pp.completions)"
                + " FILTER (WHERE pp.completions > 0) AS median_completions"
                + " FROM mission_templates t"
                + " JOIN events e ON e.id = t.event_id"
                + " LEFT JOIN per_player pp ON pp.template_id = t.id"
                + " WHERE t.event_id = :eventId";
        if (week != null) {
            sql += " AND " + TEMPLATE_WEEK + " = :week";
            params.put("week", week);
        }
        sql += " GROUP BY t.id, t.code, t.name, t.type, t.pool, t.repeatable, week"
                + " ORDER BY week ASC, players_completed DESC, t.code ASC";

        return queryRunner.list(sql, params, row -> {
            long players = num(row[7]);
            long playersCompleted = num(row[8]);
            long completions = num(row[11]);
            return MissionCalibrationResponse.builder()
                    .templateId((UUID) row[0])
                    .templateCode((String) row[1])
                    .templateName((String) row[2])
                    .type(MissionType.valueOf((String) row[3]))
                    .pool(MissionPool.valueOf((String) row[4]))
                    .repeatable((Boolean) row[5])
                    .week(((Number) row[6]).intValue())
                    .players(players)
                    .playersCompleted(playersCompleted)
                    .playersExpired(num(row[9]))
                    .playersOpen(num(row[10]))
                    .playerCompletionRate(rate(playersCompleted, players))
                    .assigned(players)
                    .completed(completions)
                    .expired(num(row[9]))
                    .stillOpen(num(row[10]))
                    .completionRate(rate(playersCompleted, players))
                    .medianCompletionsPerPlayer(dbl(row[14]))
                    .xpPaid(num(row[12]))
                    .itemsAwarded(num(row[13]))
                    .build();
        });
    }

    private String countryFilter(String country, Map<String, Object> params) {
        if (country == null) {
            return "";
        }
        params.put("country", country);
        return PARTICIPANT_COUNTRY;
    }

    @Cacheable(value = "statistics", key = "'evt:missionboard:' + #eventId + ':' + #templateId + ':' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<EventMissionLeaderboardResponse> getMissionLeaderboard(UUID eventId, UUID templateId, String country,
            Pageable pageable) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("eventId", eventId);
        String normalizedCountry = StatsQueryRunner.normalizeCountry(country);

        String sql = "SELECT RANK() OVER (ORDER BY COUNT(*) FILTER (WHERE m.status = 'completed') DESC) AS rank,"
                + " u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country,"
                + " COUNT(*) FILTER (WHERE m.status = 'completed') AS completions,"
                + " COALESCE(SUM(m.xp_reward) FILTER (WHERE m.status = 'completed'), 0) AS xp_earned,"
                + " COUNT(*) FILTER (WHERE m.item_awarded = true) AS items_awarded,"
                + " MAX(m.completed_at) AS last_completed"
                + " FROM user_missions m"
                + " JOIN mission_templates t ON t.id = m.template_id"
                + " JOIN users u ON u.id = m.user_id AND u.active = true AND u.banned = false"
                + " WHERE t.event_id = :eventId";
        if (templateId != null) {
            sql += " AND t.id = :templateId";
            params.put("templateId", templateId);
        }
        sql += countryFilter(normalizedCountry, params);
        sql += " GROUP BY u.id, u.name, u.avatar_url, u.cdn_avatar_url, u.country"
                + " HAVING COUNT(*) FILTER (WHERE m.status = 'completed') > 0"
                + " ORDER BY completions DESC, last_completed ASC, u.name ASC";

        return queryRunner.paged(sql, params, pageable, row -> EventMissionLeaderboardResponse.builder()
                .rank(num(row[0]))
                .userId(String.valueOf(((Number) row[1]).longValue()))
                .clan(ClanRefCache.forUser(((Number) row[1]).longValue()))
                .userName((String) row[2])
                .avatarUrl((String) row[3])
                .cdnAvatarUrl((String) row[4])
                .country((String) row[5])
                .completions(num(row[6]))
                .xpEarned(num(row[7]))
                .itemsAwarded(num(row[8]))
                .lastCompletedAt(row[9] == null ? null : (Instant) row[9])
                .build());
    }

    @Cacheable(value = "statistics", key = "'evt:participation:' + #eventIds + ':' + #country + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<EventParticipationResponse> getParticipation(List<UUID> eventIds, String country, Pageable pageable) {
        Map<String, Object> params = new LinkedHashMap<>();
        String normalizedCountry = StatsQueryRunner.normalizeCountry(country);
        String profileJoin = " LEFT JOIN user_event_profiles p ON p.event_id = e.id"
                + " LEFT JOIN users u ON u.id = p.user_id AND u.active = true AND u.banned = false"
                + countryFilter(normalizedCountry, params);
        String sql = "SELECT e.id, e.title, e.slug, e.icon_url, e.starts_at, e.ends_at,"
                + " COUNT(u.id) AS participants,"
                + " COUNT(*) FILTER (WHERE u.id IS NOT NULL AND p.completed_at IS NOT NULL) AS finishers,"
                + " COALESCE(SUM(p.missions_completed) FILTER (WHERE u.id IS NOT NULL), 0) AS missions_completed"
                + " FROM events e"
                + profileJoin
                + " WHERE e.active = true";
        if (eventIds != null && !eventIds.isEmpty()) {
            sql += " AND e.id IN (:eventIds)";
            params.put("eventIds", eventIds);
        }
        sql += " GROUP BY e.id, e.title, e.slug, e.icon_url, e.starts_at, e.ends_at"
                + " ORDER BY e.starts_at DESC";

        return queryRunner.paged(sql, params, pageable, row -> {
            long participants = num(row[6]);
            long finishers = num(row[7]);
            long missions = num(row[8]);
            return EventParticipationResponse.builder()
                    .eventId((UUID) row[0])
                    .title((String) row[1])
                    .slug((String) row[2])
                    .iconUrl((String) row[3])
                    .startsAt((Instant) row[4])
                    .endsAt((Instant) row[5])
                    .participants(participants)
                    .finishers(finishers)
                    .finishRate(rate(finishers, participants))
                    .missionsCompleted(missions)
                    .averageMissionsPerParticipant(participants <= 0 ? null : (double) missions / participants)
                    .build();
        });
    }
}
