package com.accsaber.backend.repository.clan.war;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.clan.war.ClanWarHit;

public interface ClanWarHitRepository extends JpaRepository<ClanWarHit, UUID> {

    interface VictimScoreView {
        UUID getScoreId();

        Long getUserId();

        int getScore();
    }

    @Query(value = """
            SELECT s.id AS scoreId, s.user_id AS userId, s.score AS score
            FROM scores s
            WHERE s.map_difficulty_id = :mapDifficultyId AND s.active AND s.user_id IN (:userIds)
            """, nativeQuery = true)
    List<VictimScoreView> findActiveScores(@Param("mapDifficultyId") UUID mapDifficultyId,
            @Param("userIds") Collection<Long> userIds);

    interface PriorHitView {
        Long getVictimId();

        UUID getVictimScoreId();

        boolean isChip();
    }

    @Query(value = """
            SELECT h.victim_user_id AS victimId, h.victim_score_id AS victimScoreId, h.chip AS chip
            FROM clan_war_hits h
            WHERE h.war_id = :warId AND h.attacker_user_id = :attackerId AND h.map_difficulty_id = :mapDifficultyId
            """, nativeQuery = true)
    List<PriorHitView> findPriorHits(@Param("warId") UUID warId, @Param("attackerId") Long attackerId,
            @Param("mapDifficultyId") UUID mapDifficultyId);

    long countByWar_IdAndVictim_IdAndVictimCycleAndChipFalse(UUID warId, Long victimId, int victimCycle);

    List<ClanWarHit> findByWar_IdAndVictim_IdAndVictimCycle(UUID warId, Long victimId, int victimCycle);

    @Query(value = """
            SELECT h FROM ClanWarHit h
            JOIN FETCH h.attacker JOIN FETCH h.victim
            WHERE h.war.id = :warId
              AND (:userId IS NULL OR h.attacker.id = :userId OR h.victim.id = :userId)
            ORDER BY h.createdAt DESC
            """,
            countQuery = """
                    SELECT COUNT(h) FROM ClanWarHit h
                    WHERE h.war.id = :warId
                      AND (:userId IS NULL OR h.attacker.id = :userId OR h.victim.id = :userId)
                    """)
    Page<ClanWarHit> findPageByWarId(@Param("warId") UUID warId, @Param("userId") Long userId, Pageable pageable);

    interface TimelinePointView {
        Instant getAt();

        UUID getClanId();

        double getDamage();

        long getHits();

        long getBreaks();

        double getStandingMoved();
    }

    @Query(value = """
            SELECT date_trunc('hour', h.created_at) AS at, p.clan_id AS clanId, SUM(h.damage) AS damage,
                   COUNT(*) AS hits, COUNT(*) FILTER (WHERE h.broke) AS breaks,
                   SUM(h.standing_moved) AS standingMoved
            FROM clan_war_hits h
            JOIN clan_war_participants p ON p.war_id = h.war_id AND p.user_id = h.attacker_user_id
            WHERE h.war_id = :warId
            GROUP BY 1, 2
            ORDER BY 1
            """, nativeQuery = true)
    List<TimelinePointView> findHourlyTimeline(@Param("warId") UUID warId);
}
