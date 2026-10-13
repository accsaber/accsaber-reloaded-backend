package com.accsaber.backend.repository.clan.war;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.clan.war.ClanWarSide;

public interface ClanWarSideRepository extends JpaRepository<ClanWarSide, ClanWarSide.Key> {

    @Query("""
            SELECT s FROM ClanWarSide s JOIN FETCH s.clan LEFT JOIN FETCH s.leadUser
            WHERE s.war.id IN :warIds
            ORDER BY s.joinedAt, s.clan.id
            """)
    List<ClanWarSide> findByWarIds(@Param("warIds") Collection<UUID> warIds);

    default List<ClanWarSide> findByWarId(UUID warId) {
        return findByWarIds(List.of(warId));
    }

    Optional<ClanWarSide> findByWar_IdAndClan_Id(UUID warId, UUID clanId);

    @Query(value = """
            SELECT COUNT(*) > 0 FROM clan_war_sides s
            JOIN clan_wars w ON w.id = s.war_id
            WHERE s.clan_id = :clanId AND s.left_at IS NULL AND w.status <> 'ended'
              AND s.clan_id NOT IN (w.attacker_clan_id, w.defender_clan_id)
            """, nativeQuery = true)
    boolean existsOpenAllySide(@Param("clanId") UUID clanId);

    @Query(value = """
            SELECT s.war_id FROM clan_war_sides s
            JOIN clan_wars w ON w.id = s.war_id
            WHERE s.clan_id = :clanId AND s.left_at IS NULL AND w.status <> 'ended'
              AND s.clan_id NOT IN (w.attacker_clan_id, w.defender_clan_id)
            """, nativeQuery = true)
    List<UUID> findOpenAllyWarIds(@Param("clanId") UUID clanId);
}
