package com.accsaber.backend.repository.clan.war;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

        UUID getMapDifficultyId();

        boolean getHit();

        boolean getMissing();

        long getChips();

        long getBreaks();
    }

    record Streak(long hits, long missing, long chips, Set<UUID> maps, Map<UUID, Long> mapBreaks) {

        public static final Streak NONE = new Streak(0, 0, 0, Set.of(), Map.of());

        public boolean repeats(UUID mapDifficultyId) {
            return mapDifficultyId != null && maps.contains(mapDifficultyId);
        }

        public Streak on(UUID mapDifficultyId) {
            return repeats(mapDifficultyId) ? NONE : this;
        }

        public long breaksOn(UUID mapDifficultyId) {
            return mapDifficultyId == null ? 0 : mapBreaks.getOrDefault(mapDifficultyId, 0L);
        }

        private Streak plus(CycleHitsView view) {
            Set<UUID> hitMaps = new HashSet<>(maps);
            if (view.getHit() || view.getMissing()) {
                hitMaps.add(view.getMapDifficultyId());
            }
            Map<UUID, Long> breaks = new HashMap<>(mapBreaks);
            if (view.getBreaks() > 0) {
                breaks.put(view.getMapDifficultyId(), view.getBreaks());
            }
            return new Streak(hits + (view.getHit() ? 1 : 0), missing + (view.getMissing() ? 1 : 0),
                    chips + view.getChips(), hitMaps, breaks);
        }
    }

    @Query(value = """
            WITH scoped AS (
                SELECT h.victim_user_id, h.map_difficulty_id, h.chip, h.victim_score_id,
                       h.victim_cycle = p.guard_cycle AS in_cycle,
                       CASE WHEN NOT h.broke THEN NULL WHEN NOT h.chip THEN h.map_difficulty_id
                            ELSE (SELECT l.map_difficulty_id FROM clan_war_hits l
                                  WHERE l.war_id = h.war_id AND l.victim_user_id = h.victim_user_id
                                    AND l.victim_cycle = h.victim_cycle AND NOT l.chip AND l.created_at <= h.created_at
                                  ORDER BY l.created_at DESC LIMIT 1) END AS broke_on
                FROM clan_war_hits h
                JOIN clan_war_participants p ON p.war_id = h.war_id AND p.user_id = h.victim_user_id
                WHERE h.war_id = :warId AND h.victim_user_id IN (:victimIds)
            )
            SELECT s.victim_user_id AS victimId, s.map_difficulty_id AS mapDifficultyId,
                   bool_or(NOT s.chip AND s.victim_score_id IS NOT NULL AND s.in_cycle) AS hit,
                   bool_or(NOT s.chip AND s.victim_score_id IS NULL AND s.in_cycle) AS missing,
                   COUNT(*) FILTER (WHERE s.chip AND s.in_cycle)
                       + GREATEST(0, COUNT(*) FILTER (WHERE NOT s.chip AND s.in_cycle) - 1) AS chips,
                   (SELECT COUNT(*) FROM scoped b
                    WHERE b.victim_user_id = s.victim_user_id AND b.broke_on = s.map_difficulty_id) AS breaks
            FROM scoped s
            GROUP BY s.victim_user_id, s.map_difficulty_id
            HAVING bool_or(s.in_cycle) OR EXISTS (SELECT 1 FROM scoped b
                    WHERE b.victim_user_id = s.victim_user_id AND b.broke_on = s.map_difficulty_id)
            """, nativeQuery = true)
    List<CycleHitsView> findCycleHits(@Param("warId") UUID warId, @Param("victimIds") Collection<Long> victimIds);

    default Map<Long, Streak> cycleHits(UUID warId, Collection<Long> victimIds) {
        Map<Long, Streak> streaks = new HashMap<>();
        for (CycleHitsView view : findCycleHits(warId, victimIds)) {
            streaks.put(view.getVictimId(), streaks.getOrDefault(view.getVictimId(), Streak.NONE).plus(view));
        }
        return streaks;
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
            JOIN FETCH h.mapDifficulty d JOIN FETCH d.map LEFT JOIN FETCH d.category
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
