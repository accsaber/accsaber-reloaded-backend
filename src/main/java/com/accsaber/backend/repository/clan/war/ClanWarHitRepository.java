package com.accsaber.backend.repository.clan.war;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.clan.war.ClanWarHit;

public interface ClanWarHitRepository extends JpaRepository<ClanWarHit, UUID> {

    interface VictimScoreView {
        UUID getScoreId();

        UUID getMapDifficultyId();

        Long getUserId();

        int getScore();
    }

    @Query(value = """
            SELECT s.id AS scoreId, s.map_difficulty_id AS mapDifficultyId, s.user_id AS userId, s.score AS score
            FROM scores s
            WHERE s.map_difficulty_id IN (:mapDifficultyIds) AND s.active AND s.user_id IN (:userIds)
            """, nativeQuery = true)
    List<VictimScoreView> findActiveScores(@Param("mapDifficultyIds") Collection<UUID> mapDifficultyIds,
            @Param("userIds") Collection<Long> userIds);

    interface CycleHitsView {
        Long getVictimId();

        long getHits();
    }

    @Query("""
            SELECT h.victim.id AS victimId, COUNT(h) AS hits
            FROM ClanWarHit h, ClanWarParticipant p
            WHERE h.war.id = :warId AND p.war.id = :warId AND p.user.id = h.victim.id
              AND h.victimCycle = p.guardCycle AND h.chip = false AND h.victim.id IN :victimIds
            GROUP BY h.victim.id
            """)
    List<CycleHitsView> findCycleHits(@Param("warId") UUID warId, @Param("victimIds") Collection<Long> victimIds);

    default Map<Long, Long> cycleHits(UUID warId, Collection<Long> victimIds) {
        return findCycleHits(warId, victimIds).stream()
                .collect(Collectors.toMap(CycleHitsView::getVictimId, CycleHitsView::getHits));
    }

    interface BreakXpView {
        Long getUserId();

        double getXp();
    }

    @Query("""
            SELECT h.attacker.id AS userId, SUM(h.xpAwarded) AS xp
            FROM ClanWarHit h
            WHERE h.war.id = :warId AND h.xpAwarded IS NOT NULL
            GROUP BY h.attacker.id
            """)
    List<BreakXpView> findBreakXp(@Param("warId") UUID warId);

    default Map<Long, Double> breakXp(UUID warId) {
        return findBreakXp(warId).stream().collect(Collectors.toMap(BreakXpView::getUserId, BreakXpView::getXp));
    }

    List<ClanWarHit> findByWar_IdAndVictim_IdAndVictimCycle(UUID warId, Long victimId, int victimCycle);

    @Query(value = """
            SELECT h FROM ClanWarHit h
            JOIN FETCH h.attacker JOIN FETCH h.victim
            WHERE h.war.id = :warId
              AND (:userId IS NULL OR h.attacker.id = :userId OR h.victim.id = :userId)
              AND (:chip IS NULL OR h.chip = :chip)
              AND (:clanId IS NULL OR EXISTS (
                  SELECT 1 FROM ClanWarParticipant p
                  WHERE p.war = h.war AND p.clan.id = :clanId AND (p.user = h.attacker OR p.user = h.victim)))
            ORDER BY h.createdAt DESC
            """,
            countQuery = """
                    SELECT COUNT(h) FROM ClanWarHit h
                    WHERE h.war.id = :warId
                      AND (:userId IS NULL OR h.attacker.id = :userId OR h.victim.id = :userId)
                      AND (:chip IS NULL OR h.chip = :chip)
                      AND (:clanId IS NULL OR EXISTS (
                          SELECT 1 FROM ClanWarParticipant p
                          WHERE p.war = h.war AND p.clan.id = :clanId AND (p.user = h.attacker OR p.user = h.victim)))
                    """)
    Page<ClanWarHit> findPageByWarId(@Param("warId") UUID warId, @Param("userId") Long userId,
            @Param("clanId") UUID clanId, @Param("chip") Boolean chip, Pageable pageable);

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
