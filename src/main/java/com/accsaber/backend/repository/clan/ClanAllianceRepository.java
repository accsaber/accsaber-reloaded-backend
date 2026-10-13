package com.accsaber.backend.repository.clan;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.clan.ClanAlliance;
import com.accsaber.backend.model.entity.clan.ClanAllianceStatus;

public interface ClanAllianceRepository extends JpaRepository<ClanAlliance, UUID> {

    interface WarsTogetherView {
        UUID getAllianceId();

        long getWars();
    }

    @Query("""
            SELECT a FROM ClanAlliance a
            JOIN FETCH a.clanA JOIN FETCH a.clanB JOIN FETCH a.proposedByUser LEFT JOIN FETCH a.endedByUser
            WHERE a.id = :id
            """)
    Optional<ClanAlliance> findWithRefsById(@Param("id") UUID id);

    @Query(value = """
            SELECT a FROM ClanAlliance a
            JOIN FETCH a.clanA JOIN FETCH a.clanB JOIN FETCH a.proposedByUser
            WHERE (a.clanA.id = :clanId OR a.clanB.id = :clanId) AND a.status = :status
            ORDER BY a.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(a) FROM ClanAlliance a
            WHERE (a.clanA.id = :clanId OR a.clanB.id = :clanId) AND a.status = :status
            """)
    Page<ClanAlliance> findPageByClanIdAndStatus(@Param("clanId") UUID clanId,
            @Param("status") ClanAllianceStatus status, Pageable pageable);

    @Query("""
            SELECT a FROM ClanAlliance a
            JOIN FETCH a.clanA JOIN FETCH a.clanB
            WHERE (a.clanA.id = :clanId OR a.clanB.id = :clanId)
              AND a.status IN (com.accsaber.backend.model.entity.clan.ClanAllianceStatus.pending,
                               com.accsaber.backend.model.entity.clan.ClanAllianceStatus.active)
            """)
    List<ClanAlliance> findOpenByClanId(@Param("clanId") UUID clanId);

    @Query("""
            SELECT COUNT(a) > 0 FROM ClanAlliance a
            WHERE a.clanA.id = :clanAId AND a.clanB.id = :clanBId
              AND a.status IN (com.accsaber.backend.model.entity.clan.ClanAllianceStatus.pending,
                               com.accsaber.backend.model.entity.clan.ClanAllianceStatus.active)
            """)
    boolean existsOpenBetween(@Param("clanAId") UUID clanAId, @Param("clanBId") UUID clanBId);

    @Query("""
            SELECT COUNT(a) > 0 FROM ClanAlliance a
            WHERE a.status = com.accsaber.backend.model.entity.clan.ClanAllianceStatus.active
              AND ((a.clanA.id = :first AND a.clanB.id = :second) OR (a.clanA.id = :second AND a.clanB.id = :first))
            """)
    boolean existsActiveBetween(@Param("first") UUID first, @Param("second") UUID second);

    @Query("""
            SELECT COUNT(a) FROM ClanAlliance a
            WHERE (a.clanA.id = :clanId OR a.clanB.id = :clanId)
              AND a.status = com.accsaber.backend.model.entity.clan.ClanAllianceStatus.active
            """)
    long countActiveByClanId(@Param("clanId") UUID clanId);

    @Query(value = """
            SELECT a.clan_b_id FROM clan_alliances a WHERE a.status = 'active' AND a.clan_a_id IN (:clanIds)
            UNION
            SELECT a.clan_a_id FROM clan_alliances a WHERE a.status = 'active' AND a.clan_b_id IN (:clanIds)
            """, nativeQuery = true)
    List<UUID> findActiveAllyIds(@Param("clanIds") Collection<UUID> clanIds);

    @Query(value = """
            SELECT a.id AS allianceId, COUNT(DISTINCT sa.war_id) AS wars
            FROM clan_alliances a
            JOIN clan_war_sides sa ON sa.clan_id = a.clan_a_id
            JOIN clan_war_sides sb ON sb.war_id = sa.war_id AND sb.clan_id = a.clan_b_id AND sb.team = sa.team
            WHERE a.id IN (:allianceIds)
            GROUP BY a.id
            """, nativeQuery = true)
    List<WarsTogetherView> findWarsFoughtTogether(@Param("allianceIds") Collection<UUID> allianceIds);

}
