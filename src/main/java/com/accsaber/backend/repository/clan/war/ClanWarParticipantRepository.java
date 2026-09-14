package com.accsaber.backend.repository.clan.war;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
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
            JOIN FETCH p.user LEFT JOIN FETCH p.duelTarget
            WHERE p.war.id = :warId
            ORDER BY p.leftAt ASC NULLS FIRST, p.contribution DESC, p.joinedAt ASC
            """,
            countQuery = "SELECT COUNT(p) FROM ClanWarParticipant p WHERE p.war.id = :warId")
    Page<ClanWarParticipant> findPageByWarId(@Param("warId") UUID warId, Pageable pageable);

    @Query("SELECT p FROM ClanWarParticipant p WHERE p.war.id = :warId AND p.leftAt IS NULL")
    List<ClanWarParticipant> findActiveByWarId(@Param("warId") UUID warId);

    @Query(value = """
            SELECT s.user_id AS userId, s.skill_level AS skill
            FROM user_category_skills s
            JOIN categories c ON c.id = s.category_id AND c.code = 'overall' AND c.active
            WHERE s.user_id IN (:userIds)
            """, nativeQuery = true)
    List<ClanMemberRepository.MemberSkillView> findOverallSkills(@Param("userIds") Collection<Long> userIds);

    @Query("""
            SELECT p FROM ClanWarParticipant p
            WHERE p.war.id = :warId
            ORDER BY p.contribution DESC, p.joinedAt ASC, p.user.id ASC
            """)
    List<ClanWarParticipant> findByWarIdInContributionOrder(@Param("warId") UUID warId);

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
                 WHERE p.user_id = :userId AND w.status IN ('active', 'ended')) AS warsFought,
                (SELECT COUNT(*) FROM clan_war_participants p JOIN clan_wars w ON w.id = p.war_id
                 WHERE p.user_id = :userId
                   AND ((w.outcome = 'attacker_won' AND p.clan_id = w.attacker_clan_id)
                     OR (w.outcome = 'defender_won' AND p.clan_id = w.defender_clan_id))) AS warsWon,
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
