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

    Page<ClanXpGrant> findByClan_IdOrderByCreatedAtDesc(UUID clanId, Pageable pageable);

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

    interface MemberPlayXpView {
        UUID getClanId();

        double getXp();
    }

    @Query(value = """
            SELECT m.clan_id AS clanId, SUM(s.xp_gained) AS xp
            FROM scores s
            JOIN clan_members m
              ON m.user_id = s.user_id
             AND m.joined_at <= COALESCE(s.time_set, s.created_at)
             AND (m.left_at IS NULL OR m.left_at > COALESCE(s.time_set, s.created_at))
            JOIN clans c ON c.id = m.clan_id AND c.active
            WHERE ((s.time_set >= :from AND s.time_set < :to)
                   OR (s.time_set IS NULL AND s.created_at >= :from AND s.created_at < :to))
              AND s.xp_gained > 0
              AND NOT EXISTS (
                  SELECT 1 FROM clan_xp_grants g
                  WHERE g.clan_id = m.clan_id AND g.source = 'daily_play' AND g.source_id = :day)
            GROUP BY m.clan_id
            """, nativeQuery = true)
    List<MemberPlayXpView> sumUngrantedMemberPlayXp(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("day") String day);
}
