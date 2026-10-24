package com.accsaber.backend.repository.clan.war;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.clan.war.ClanWarParticipant;
import com.accsaber.backend.repository.clan.ClanMemberRepository;

public interface ClanWarParticipantRepository extends JpaRepository<ClanWarParticipant, ClanWarParticipant.Key> {

    @Query("SELECT p FROM ClanWarParticipant p WHERE p.war.id = :warId")
    List<ClanWarParticipant> findByWarId(@Param("warId") UUID warId);

    @Query(value = """
            SELECT p FROM ClanWarParticipant p
            JOIN FETCH p.user LEFT JOIN FETCH p.target
            LEFT JOIN Category c ON c.code = 'overall' AND c.active = true
            LEFT JOIN UserCategorySkill s ON s.user = p.user AND s.category = c
            WHERE p.war.id = :warId
            ORDER BY p.leftAt ASC NULLS FIRST, s.skillLevel DESC NULLS LAST, p.joinedAt ASC
            """,
            countQuery = "SELECT COUNT(p) FROM ClanWarParticipant p WHERE p.war.id = :warId")
    Page<ClanWarParticipant> findPageByWarId(@Param("warId") UUID warId, Pageable pageable);

    @Query("SELECT p FROM ClanWarParticipant p JOIN FETCH p.user WHERE p.war.id = :warId AND p.leftAt IS NULL")
    List<ClanWarParticipant> findActiveByWarId(@Param("warId") UUID warId);

    @Query("SELECT p.user.id FROM ClanWarParticipant p WHERE p.war.id = :warId AND p.leftAt IS NULL")
    List<Long> findOpenUserIds(@Param("warId") UUID warId);

    @Query("SELECT p.clan.id FROM ClanWarParticipant p WHERE p.war.id = :warId AND p.user.id = :userId AND p.leftAt IS NULL")
    Optional<UUID> findOpenClanId(@Param("warId") UUID warId, @Param("userId") Long userId);

    @Query(value = """
            SELECT COALESCE(AVG(CASE WHEN EXISTS (
                       SELECT 1 FROM scores s
                       WHERE s.user_id = p.user_id AND COALESCE(s.time_set, s.created_at) >= :since)
                   THEN 1.0 ELSE 0.0 END), 0.0)
            FROM clan_war_participants p
            JOIN clan_war_sides sd ON sd.war_id = p.war_id AND sd.clan_id = p.clan_id
            WHERE p.war_id = :warId AND sd.team = :team
            """, nativeQuery = true)
    double findRecentlyActiveShare(@Param("warId") UUID warId, @Param("team") String team,
            @Param("since") Instant since);

    @Query(value = """
            SELECT s.user_id AS userId, s.skill_level AS skill
            FROM user_category_skills s
            JOIN categories c ON c.id = s.category_id AND c.code = 'overall' AND c.active
            WHERE s.user_id IN (:userIds)
            """, nativeQuery = true)
    List<ClanMemberRepository.MemberSkillView> findOverallSkills(@Param("userIds") Collection<Long> userIds);

    @Query("""
            SELECT p FROM ClanWarParticipant p JOIN FETCH p.user
            WHERE p.war.id = :warId
            ORDER BY p.contribution DESC, p.joinedAt ASC, p.user.id ASC
            """)
    List<ClanWarParticipant> findByWarIdInContributionOrder(@Param("warId") UUID warId);

    String GUARD_DUE = """
            p.left_at IS NULL AND p.broken_at IS NOT NULL AND (p.broken_at < :cutoff OR EXISTS (
                SELECT 1 FROM clan_wars ww
                WHERE ww.id = p.war_id AND ww.wiped_at < :wipedBefore AND p.broken_at <= ww.wiped_at))
            """;

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE clan_war_participants p SET guard = :guard, guard_cycle = p.guard_cycle + 1, broken_at = NULL
            WHERE p.war_id = :warId AND
            """ + GUARD_DUE, nativeQuery = true)
    int restoreGuards(@Param("warId") UUID warId, @Param("cutoff") Instant cutoff,
            @Param("wipedBefore") Instant wipedBefore, @Param("guard") double guard);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE ClanWarParticipant p SET p.xpAwarded = :xp, p.rewardedAt = :now
            WHERE p.war.id = :warId AND p.user.id = :userId AND p.rewardedAt IS NULL
            """)
    int markRewarded(@Param("warId") UUID warId, @Param("userId") Long userId, @Param("xp") double xp,
            @Param("now") Instant now);

    interface PlayerWarStatsView {
        long getWarsFought();

        long getWarsWon();

        long getHits();

        long getBreaksDealt();

        long getBreaksSuffered();

        double getStandingMoved();

        double getContribution();

        double getWarXp();
    }

    @Query(value = """
            SELECT
                (SELECT COUNT(*) FROM clan_war_participants p JOIN clan_wars w ON w.id = p.war_id
                 WHERE p.user_id = :userId
                   AND (w.status = 'active' OR (w.status = 'ended' AND w.ended_at >= w.starts_at))) AS warsFought,
                (SELECT COUNT(*) FROM clan_war_participants p JOIN clan_wars w ON w.id = p.war_id
                 JOIN clan_war_sides sd ON sd.war_id = p.war_id AND sd.clan_id = p.clan_id
                 WHERE p.user_id = :userId
                   AND ((w.outcome = 'attacker_won' AND sd.team = 'attack')
                     OR (w.outcome = 'defender_won' AND sd.team = 'defend'))) AS warsWon,
                (SELECT COUNT(*) FROM clan_war_hits h WHERE h.attacker_user_id = :userId) AS hits,
                (SELECT COUNT(*) FROM clan_war_hits h WHERE h.attacker_user_id = :userId AND h.broke) AS breaksDealt,
                (SELECT COUNT(*) FROM clan_war_hits h WHERE h.victim_user_id = :userId AND h.broke) AS breaksSuffered,
                (SELECT COALESCE(SUM(h.standing_moved), 0) FROM clan_war_hits h
                 WHERE h.attacker_user_id = :userId) AS standingMoved,
                (SELECT COALESCE(SUM(p.contribution), 0) FROM clan_war_participants p
                 WHERE p.user_id = :userId) AS contribution,
                (SELECT COALESCE(SUM(h.xp_awarded), 0) FROM clan_war_hits h WHERE h.attacker_user_id = :userId)
                  + (SELECT COALESCE(SUM(p.xp_awarded), 0) FROM clan_war_participants p
                     WHERE p.user_id = :userId AND p.rewarded_at IS NOT NULL) AS warXp
            """, nativeQuery = true)
    PlayerWarStatsView findPlayerWarStats(@Param("userId") Long userId);
}
