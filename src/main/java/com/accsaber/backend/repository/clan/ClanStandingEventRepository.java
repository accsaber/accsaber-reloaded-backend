package com.accsaber.backend.repository.clan;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.clan.ClanStandingEvent;

public interface ClanStandingEventRepository extends JpaRepository<ClanStandingEvent, UUID> {

    Page<ClanStandingEvent> findBySeason_IdAndClan_IdOrderByCreatedAtDesc(UUID seasonId, UUID clanId,
            Pageable pageable);

    interface ClanStandingView {
        UUID getClanId();

        double getStanding();
    }

    @Query(value = """
            SELECT x.clan_id AS clanId, SUM(x.amount) AS standing
            FROM (
                SELECT e.clan_id, e.amount
                FROM clan_wars w
                JOIN clan_war_sides s ON s.war_id = w.id
                JOIN clan_standing_events e ON e.season_id = w.season_id AND e.clan_id = s.clan_id
                 AND e.source = 'war_win' AND e.source_id = CAST(w.id AS TEXT)
                WHERE w.id = :warId
                UNION ALL
                SELECT e.clan_id, e.amount
                FROM clan_war_hits h
                JOIN clan_wars w ON w.id = h.war_id
                JOIN clan_war_sides s ON s.war_id = h.war_id
                JOIN clan_standing_events e ON e.season_id = w.season_id AND e.clan_id = s.clan_id
                 AND e.source = 'war_break' AND e.source_id = CAST(h.id AS TEXT)
                WHERE h.war_id = :warId AND h.broke
                UNION ALL
                SELECT e.clan_id, e.amount
                FROM clan_wars w
                JOIN clan_war_sides s ON s.war_id = w.id
                JOIN clan_standing_events e ON e.season_id = w.season_id AND e.clan_id = s.clan_id
                 AND e.source = 'war_break' AND e.source_id LIKE 'forfeit:' || CAST(w.id AS TEXT) || ':%'
                WHERE w.id = :warId
            ) x
            GROUP BY x.clan_id
            """, nativeQuery = true)
    List<ClanStandingView> findWarStanding(@Param("warId") UUID warId);

    default Map<UUID, Double> warStanding(UUID warId) {
        return findWarStanding(warId).stream()
                .collect(Collectors.toMap(ClanStandingView::getClanId, ClanStandingView::getStanding));
    }

    @Modifying
    @Query(value = """
            INSERT INTO clan_standing_events (season_id, clan_id, amount, source, source_id)
            VALUES (:seasonId, :clanId, :amount, :source, :sourceId)
            ON CONFLICT (season_id, clan_id, source, source_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("seasonId") UUID seasonId, @Param("clanId") UUID clanId, @Param("amount") double amount,
            @Param("source") String source, @Param("sourceId") String sourceId);
}
