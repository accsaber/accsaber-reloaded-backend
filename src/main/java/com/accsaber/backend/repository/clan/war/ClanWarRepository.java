package com.accsaber.backend.repository.clan.war;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.entity.clan.war.ClanWar;

import jakarta.persistence.LockModeType;

public interface ClanWarRepository extends JpaRepository<ClanWar, UUID> {

    @Query("""
            SELECT COUNT(w) > 0 FROM ClanWar w
            WHERE w.attackerClan.id = :attackerId
              AND (:defenderId IS NULL OR w.defenderClan.id = :defenderId)
              AND w.status <> com.accsaber.backend.model.entity.clan.war.ClanWarStatus.ended
            """)
    boolean existsOpenAttack(@Param("attackerId") UUID attackerId, @Param("defenderId") UUID defenderId);

    @Query("""
            SELECT MAX(w.endedAt) FROM ClanWar w
            WHERE w.attackerClan.id = :attackerId AND w.defenderClan.id = :defenderId
            """)
    Optional<Instant> findLastAttackEndedAt(@Param("attackerId") UUID attackerId,
            @Param("defenderId") UUID defenderId);

    @Query("""
            SELECT w FROM ClanWar w
            JOIN FETCH w.attackerClan JOIN FETCH w.defenderClan JOIN FETCH w.declaredBy
            WHERE w.id = :id
            """)
    Optional<ClanWar> findWithRefsById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT w FROM ClanWar w
            JOIN FETCH w.attackerClan JOIN FETCH w.defenderClan
            WHERE w.id = :id
            """)
    Optional<ClanWar> findByIdForUpdate(@Param("id") UUID id);

    default ClanWar lockOrThrow(UUID id) {
        return findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("ClanWar", id));
    }

    @Query(value = """
            SELECT w FROM ClanWar w
            JOIN FETCH w.attackerClan JOIN FETCH w.defenderClan JOIN FETCH w.declaredBy
            WHERE (:clanId IS NULL OR EXISTS (SELECT 1 FROM ClanWarSide s WHERE s.war = w AND s.clan.id = :clanId))
              AND (:open = false OR w.status <> com.accsaber.backend.model.entity.clan.war.ClanWarStatus.ended)
            ORDER BY w.declaredAt DESC
            """,
            countQuery = """
            SELECT COUNT(w) FROM ClanWar w
            WHERE (:clanId IS NULL OR EXISTS (SELECT 1 FROM ClanWarSide s WHERE s.war = w AND s.clan.id = :clanId))
              AND (:open = false OR w.status <> com.accsaber.backend.model.entity.clan.war.ClanWarStatus.ended)
            """)
    Page<ClanWar> findPage(@Param("clanId") UUID clanId, @Param("open") boolean open, Pageable pageable);

    @Query("""
            SELECT w.id FROM ClanWar w
            WHERE (w.attackerClan.id = :clanId OR w.defenderClan.id = :clanId)
              AND w.status <> com.accsaber.backend.model.entity.clan.war.ClanWarStatus.ended
            """)
    List<UUID> findOpenIdsLedByClanId(@Param("clanId") UUID clanId);

    @Query("""
            SELECT w.id FROM ClanWar w
            WHERE EXISTS (SELECT 1 FROM ClanWarSide s WHERE s.war = w AND s.clan.id = :clanId AND s.leftAt IS NULL)
              AND w.status IN (com.accsaber.backend.model.entity.clan.war.ClanWarStatus.preparing,
                               com.accsaber.backend.model.entity.clan.war.ClanWarStatus.active)
            """)
    List<UUID> findRosteredIdsByClanId(@Param("clanId") UUID clanId);

    @Query("""
            SELECT COUNT(w) > 0 FROM ClanWar w
            WHERE EXISTS (SELECT 1 FROM ClanWarSide s WHERE s.war = w AND s.clan.id = :clanId AND s.leftAt IS NULL)
              AND w.status <> com.accsaber.backend.model.entity.clan.war.ClanWarStatus.ended
            """)
    boolean existsOpenForClan(@Param("clanId") UUID clanId);

    @Query("""
            SELECT w.id FROM ClanWar w
            WHERE w.status = com.accsaber.backend.model.entity.clan.war.ClanWarStatus.active AND w.startsAt <= :cutoff
            ORDER BY w.startsAt
            """)
    List<UUID> findActiveStartedBefore(@Param("cutoff") Instant cutoff);

    @Query("""
            SELECT w.id FROM ClanWar w
            WHERE w.season.id = :seasonId
              AND w.status <> com.accsaber.backend.model.entity.clan.war.ClanWarStatus.ended
            """)
    List<UUID> findOpenIdsBySeasonId(@Param("seasonId") UUID seasonId);

    @Query("""
            SELECT w.id FROM ClanWar w
            WHERE w.status = com.accsaber.backend.model.entity.clan.war.ClanWarStatus.picking AND w.picksDueAt <= :now
            ORDER BY w.picksDueAt
            """)
    List<UUID> findPicksDue(@Param("now") Instant now);

    @Query("""
            SELECT w.id FROM ClanWar w
            WHERE w.status = com.accsaber.backend.model.entity.clan.war.ClanWarStatus.preparing AND w.startsAt <= :now
            ORDER BY w.startsAt
            """)
    List<UUID> findStartsDue(@Param("now") Instant now);

    @Query("""
            SELECT w.id FROM ClanWar w
            WHERE w.status = com.accsaber.backend.model.entity.clan.war.ClanWarStatus.active AND w.retreatAt <= :now
            ORDER BY w.retreatAt
            """)
    List<UUID> findRetreatsDue(@Param("now") Instant now);

    @Query(value = """
            SELECT w.id FROM clan_wars w
            WHERE w.status = 'active'
              AND COALESCE((SELECT MAX(h.created_at) FROM clan_war_hits h WHERE h.war_id = w.id), w.starts_at) < :cutoff
            ORDER BY w.starts_at
            """, nativeQuery = true)
    List<UUID> findQuietSince(@Param("cutoff") Instant cutoff);

    @Query(value = """
            SELECT w.id FROM clan_wars w
            JOIN clan_war_participants p ON p.war_id = w.id AND p.user_id = :userId AND p.left_at IS NULL
            JOIN clan_war_pool pool ON pool.war_id = w.id AND pool.map_difficulty_id = :mapDifficultyId
            WHERE w.status = 'active'
            ORDER BY w.id
            """, nativeQuery = true)
    List<UUID> findActiveIdsFighting(@Param("userId") Long userId, @Param("mapDifficultyId") UUID mapDifficultyId);

    @Query(value = """
            SELECT DISTINCT pool.map_difficulty_id FROM clan_war_pool pool
            JOIN clan_wars w ON w.id = pool.war_id AND w.status = 'active'
            """, nativeQuery = true)
    List<UUID> findActivePoolDifficultyIds();

    @Query(value = """
            SELECT DISTINCT p.user_id FROM clan_war_participants p
            JOIN clan_wars w ON w.id = p.war_id AND w.status = 'active'
            WHERE p.left_at IS NULL
            """, nativeQuery = true)
    List<Long> findActiveParticipantIds();

    @Query(value = """
            SELECT w.id FROM clan_wars w
            WHERE w.status = 'ended'
              AND EXISTS (SELECT 1 FROM clan_war_participants p WHERE p.war_id = w.id AND p.rewarded_at IS NULL)
            ORDER BY w.ended_at
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findEndedUnsettled(@Param("limit") int limit);

    @Modifying
    @Query(value = """
            INSERT INTO clan_war_scored_plays (war_id, score_id) VALUES (:warId, :scoreId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int markScored(@Param("warId") UUID warId, @Param("scoreId") UUID scoreId);

    interface UnscoredPlayView {
        UUID getWarId();

        UUID getScoreId();

        Long getUserId();

        UUID getMapDifficultyId();

        int getScore();

        boolean isPersonalBest();

        Instant getSetAt();
    }

    @Query(value = """
            SELECT w.id AS warId, s.id AS scoreId, s.user_id AS userId, s.map_difficulty_id AS mapDifficultyId,
                   s.score AS score, s.supersedes_reason IS DISTINCT FROM 'Worse score' AS personalBest,
                   COALESCE(s.time_set, s.created_at) AS setAt
            FROM clan_wars w
            JOIN clan_war_participants p ON p.war_id = w.id AND p.left_at IS NULL
            JOIN clan_war_pool pool ON pool.war_id = w.id
            JOIN scores s ON s.user_id = p.user_id AND s.map_difficulty_id = pool.map_difficulty_id
            WHERE w.status = 'active'
              AND COALESCE(s.time_set, s.created_at) >= GREATEST(w.starts_at, :since)
              AND s.created_at < :settledBefore
              AND NOT s.partial
              AND NOT s.reweight_derivative
              AND s.supersedes_reason IS DISTINCT FROM 'Campaign attempt'
              AND NOT EXISTS (SELECT 1 FROM clan_war_scored_plays sp WHERE sp.war_id = w.id AND sp.score_id = s.id)
            ORDER BY COALESCE(s.time_set, s.created_at)
            LIMIT :limit
            """, nativeQuery = true)
    List<UnscoredPlayView> findUnscoredPlays(@Param("since") Instant since,
            @Param("settledBefore") Instant settledBefore, @Param("limit") int limit);
}
