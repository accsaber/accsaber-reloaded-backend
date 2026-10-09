package com.accsaber.backend.repository.clan;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.clan.ClanXpGrant;

public interface ClanXpGrantRepository extends JpaRepository<ClanXpGrant, UUID> {

    interface HistoryRowView {
        UUID getId();

        String getSource();

        String getSourceId();

        double getRawAmount();

        double getRosterFactor();

        double getAmount();

        Instant getCreatedAt();

        long getGrants();
    }

    @Query(value = """
            SELECT g.id AS id, g.source AS source, g.source_id AS sourceId, g.raw_amount AS rawAmount,
                   g.roster_factor AS rosterFactor, g.amount AS amount, g.created_at AS createdAt, 1 AS grants
            FROM clan_xp_grants g
            WHERE g.clan_id = :clanId AND g.source <> 'play'
            UNION ALL
            SELECT NULL, 'play', NULL, SUM(g.raw_amount), COALESCE(SUM(g.raw_amount) / NULLIF(SUM(g.amount), 0), 1),
                   SUM(g.amount), MAX(g.created_at), COUNT(*)
            FROM clan_xp_grants g
            WHERE g.clan_id = :clanId AND g.source = 'play'
            GROUP BY date_trunc('day', g.created_at AT TIME ZONE 'UTC')
            ORDER BY createdAt DESC
            """, countQuery = """
            SELECT (SELECT COUNT(*) FROM clan_xp_grants g WHERE g.clan_id = :clanId AND g.source <> 'play')
                 + (SELECT COUNT(DISTINCT date_trunc('day', g.created_at AT TIME ZONE 'UTC'))
                    FROM clan_xp_grants g WHERE g.clan_id = :clanId AND g.source = 'play')
            """, nativeQuery = true)
    Page<HistoryRowView> findHistory(@Param("clanId") UUID clanId, Pageable pageable);

    interface SourceXpView {
        String getSource();

        double getXp();
    }

    @Query(value = """
            SELECT g.source AS source, SUM(g.amount) AS xp
            FROM clan_xp_grants g
            WHERE g.clan_id = :clanId AND g.created_at >= :from AND g.created_at < :to
            GROUP BY g.source
            """, nativeQuery = true)
    List<SourceXpView> sumBySource(@Param("clanId") UUID clanId, @Param("from") Instant from,
            @Param("to") Instant to);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO clan_xp_grants (clan_id, source, source_id, raw_amount, roster_factor, amount)
            VALUES (:clanId, :source, :sourceId, :rawAmount, :rosterFactor, :amount)
            ON CONFLICT (clan_id, source, source_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("clanId") UUID clanId,
            @Param("source") String source,
            @Param("sourceId") String sourceId,
            @Param("rawAmount") double rawAmount,
            @Param("rosterFactor") double rosterFactor,
            @Param("amount") double amount);

    interface UngrantedPlayView {
        UUID getClanId();

        UUID getScoreId();
    }

    @Query(value = """
            SELECT m.clan_id AS clanId, s.id AS scoreId
            FROM scores s
            JOIN clan_members m
              ON m.user_id = s.user_id
             AND m.joined_at <= COALESCE(s.time_set, s.created_at)
             AND (m.left_at IS NULL OR m.left_at > COALESCE(s.time_set, s.created_at))
            JOIN clans c ON c.id = m.clan_id AND c.active
            WHERE ((s.time_set >= :from AND s.time_set < :to)
                   OR (s.time_set IS NULL AND s.created_at >= :from AND s.created_at < :to))
              AND s.xp_gained > 0
              AND NOT s.partial
              AND NOT EXISTS (
                  SELECT 1 FROM clan_xp_grants g
                  WHERE g.clan_id = m.clan_id AND g.source = 'play' AND g.source_id = CAST(s.id AS TEXT))
            """, nativeQuery = true)
    List<UngrantedPlayView> findUngrantedPlays(@Param("from") Instant from, @Param("to") Instant to);
}
