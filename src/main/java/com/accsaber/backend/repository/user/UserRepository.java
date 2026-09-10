package com.accsaber.backend.repository.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.entity.user.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByIdAndActiveTrue(Long id);

    List<User> findByActiveTrue();

    List<User> findByActiveTrueOrderByTotalXpDesc();

    @Query("""
            SELECT u FROM User u
            WHERE u.active = true AND u.banned = false AND u.totalXp > 0
            AND (:includeInactive = true OR u.playerInactive = false)
            AND (:hmd IS NULL OR EXISTS (SELECT 1 FROM Score sc WHERE sc.user = u AND sc.active = true AND sc.hmd = :hmd AND NOT EXISTS (SELECT 1 FROM Score sc3 WHERE sc3.user = u AND sc3.active = true AND sc3.timeSet > sc.timeSet)))
            ORDER BY u.totalXp DESC
            """)
    Page<User> findXpLeaderboard(@Param("includeInactive") boolean includeInactive,
            @Param("hmd") String hmd, Pageable pageable);

    @Query("""
            SELECT u FROM User u
            WHERE u.active = true AND u.banned = false AND u.totalXp > 0
            AND u.id IN (SELECT sn.userId FROM UserSearchName sn WHERE sn.searchName LIKE CONCAT('%', search_normalize(CAST(:search AS string)), '%'))
            AND (:includeInactive = true OR u.playerInactive = false)
            AND (:hmd IS NULL OR EXISTS (SELECT 1 FROM Score sc WHERE sc.user = u AND sc.active = true AND sc.hmd = :hmd AND NOT EXISTS (SELECT 1 FROM Score sc3 WHERE sc3.user = u AND sc3.active = true AND sc3.timeSet > sc.timeSet)))
            ORDER BY u.totalXp DESC
            """)
    Page<User> findXpLeaderboardWithSearch(@Param("search") String search,
            @Param("includeInactive") boolean includeInactive,
            @Param("hmd") String hmd, Pageable pageable);

    @Query("""
            SELECT u FROM User u
            WHERE u.active = true AND u.banned = false AND u.totalXp > 0
            AND LOWER(u.country) = LOWER(:country)
            AND (:includeInactive = true OR u.playerInactive = false)
            AND (:hmd IS NULL OR EXISTS (SELECT 1 FROM Score sc WHERE sc.user = u AND sc.active = true AND sc.hmd = :hmd AND NOT EXISTS (SELECT 1 FROM Score sc3 WHERE sc3.user = u AND sc3.active = true AND sc3.timeSet > sc.timeSet)))
            ORDER BY u.totalXp DESC
            """)
    Page<User> findXpLeaderboardByCountry(@Param("country") String country,
            @Param("includeInactive") boolean includeInactive,
            @Param("hmd") String hmd, Pageable pageable);

    @Query("""
            SELECT u FROM User u
            WHERE u.active = true AND u.banned = false AND u.totalXp > 0
            AND LOWER(u.country) = LOWER(:country)
            AND u.id IN (SELECT sn.userId FROM UserSearchName sn WHERE sn.searchName LIKE CONCAT('%', search_normalize(CAST(:search AS string)), '%'))
            AND (:includeInactive = true OR u.playerInactive = false)
            AND (:hmd IS NULL OR EXISTS (SELECT 1 FROM Score sc WHERE sc.user = u AND sc.active = true AND sc.hmd = :hmd AND NOT EXISTS (SELECT 1 FROM Score sc3 WHERE sc3.user = u AND sc3.active = true AND sc3.timeSet > sc.timeSet)))
            ORDER BY u.totalXp DESC
            """)
    Page<User> findXpLeaderboardByCountryWithSearch(@Param("country") String country,
            @Param("search") String search, @Param("includeInactive") boolean includeInactive,
            @Param("hmd") String hmd, Pageable pageable);

    @Query("""
            SELECT DISTINCT sn.userId FROM UserSearchName sn
            WHERE sn.searchName LIKE CONCAT('%', search_normalize(CAST(:search AS string)), '%')
            """)
    List<Long> findIdsBySearch(@Param("search") String search);

    @Query("""
            SELECT u FROM User u
            WHERE u.active = true AND u.banned = false AND u.totalXp > 0
            AND u.id IN :userIds
            AND (CAST(:country AS string) IS NULL OR LOWER(u.country) = LOWER(CAST(:country AS string)))
            AND (CAST(:search AS string) IS NULL OR u.id IN (SELECT sn.userId FROM UserSearchName sn WHERE sn.searchName LIKE CONCAT('%', search_normalize(CAST(:search AS string)), '%')))
            AND (:includeInactive = true OR u.playerInactive = false)
            AND (CAST(:hmd AS string) IS NULL OR EXISTS (SELECT 1 FROM Score sc WHERE sc.user = u AND sc.active = true AND sc.hmd = CAST(:hmd AS string) AND NOT EXISTS (SELECT 1 FROM Score sc3 WHERE sc3.user = u AND sc3.active = true AND sc3.timeSet > sc.timeSet)))
            ORDER BY u.totalXp DESC
            """)
    Page<User> findXpLeaderboardFilteredByUserIds(
            @Param("userIds") java.util.Collection<Long> userIds,
            @Param("country") String country,
            @Param("search") String search,
            @Param("includeInactive") boolean includeInactive,
            @Param("hmd") String hmd,
            Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.totalXp = u.totalXp + :xp WHERE u.id = :id")
    void addXp(@Param("id") Long id, @Param("xp") Double xp);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.missionXp = u.missionXp + :xp WHERE u.id = :id")
    void addMissionXp(@Param("id") Long id, @Param("xp") Double xp);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.campaignXp = u.campaignXp + :xp WHERE u.id = :id")
    void addCampaignXp(@Param("id") Long id, @Param("xp") Double xp);

    @Query("SELECT u.totalXp FROM User u WHERE u.id = :id")
    java.util.Optional<Double> findTotalXpById(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.itemEssence = u.itemEssence + :amount WHERE u.id = :id")
    void addItemEssence(@Param("id") Long id, @Param("amount") long amount);

    @Query("SELECT u.itemEssence FROM User u WHERE u.id = :id")
    java.util.Optional<Long> findItemEssenceById(@Param("id") Long id);

    @Query("SELECT u.reservedEssence FROM User u WHERE u.id = :id")
    java.util.Optional<Long> findReservedEssenceById(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("""
            UPDATE User u
            SET u.itemEssence = u.itemEssence - :amount,
                u.reservedEssence = u.reservedEssence + :amount
            WHERE u.id = :id AND u.itemEssence >= :amount
            """)
    int reserveEssence(@Param("id") Long id, @Param("amount") long amount);

    @Modifying
    @Transactional
    @Query("""
            UPDATE User u
            SET u.itemEssence = u.itemEssence + :amount,
                u.reservedEssence = u.reservedEssence - :amount
            WHERE u.id = :id AND u.reservedEssence >= :amount
            """)
    int releaseEssence(@Param("id") Long id, @Param("amount") long amount);

    @Modifying
    @Transactional
    @Query("""
            UPDATE User u
            SET u.reservedEssence = u.reservedEssence - :amount
            WHERE u.id = :id AND u.reservedEssence >= :amount
            """)
    int consumeReservedEssence(@Param("id") Long id, @Param("amount") long amount);

    @Modifying
    @Transactional
    @Query("""
            UPDATE User u
            SET u.itemEssence = u.itemEssence - :amount
            WHERE u.id = :id AND u.itemEssence >= :amount
            """)
    int debitEssence(@Param("id") Long id, @Param("amount") long amount);

    @Modifying
    @Transactional
    @Query(value = """
            WITH sources AS (
                SELECT s.user_id, CAST('score' AS text) AS bucket, CAST(s.xp_gained AS numeric) AS xp
                FROM scores s
                WHERE CAST(:userId AS bigint) IS NULL OR s.user_id = CAST(:userId AS bigint)
                UNION ALL
                SELECT uml.user_id, 'milestone', CAST(m.xp AS numeric)
                FROM user_milestone_links uml
                JOIN milestones m ON uml.milestone_id = m.id
                WHERE uml.completed = true
                  AND (CAST(:userId AS bigint) IS NULL OR uml.user_id = CAST(:userId AS bigint))
                UNION ALL
                SELECT umsb.user_id, 'milestone', CAST(ms.set_bonus_xp AS numeric)
                FROM user_milestone_set_bonuses umsb
                JOIN milestone_sets ms ON umsb.milestone_set_id = ms.id
                WHERE CAST(:userId AS bigint) IS NULL OR umsb.user_id = CAST(:userId AS bigint)
                UNION ALL
                SELECT ucs.user_id, 'campaign', CAST(ucs.xp_awarded AS numeric)
                FROM user_campaign_scores ucs
                WHERE ucs.active = true AND ucs.rewards_paid = true
                  AND (CAST(:userId AS bigint) IS NULL OR ucs.user_id = CAST(:userId AS bigint))
                UNION ALL
                SELECT uc.user_id, 'campaign', CAST(uc.completion_xp_awarded AS numeric)
                FROM user_campaigns uc
                WHERE uc.active = true AND uc.completion_rewards_paid = true
                  AND (CAST(:userId AS bigint) IS NULL OR uc.user_id = CAST(:userId AS bigint))
                UNION ALL
                SELECT um.user_id, 'mission', CAST(um.xp_reward AS numeric)
                FROM user_missions um
                WHERE um.status = 'completed'
                  AND um.user_id IS NOT NULL
                  AND (CAST(:userId AS bigint) IS NULL OR um.user_id = CAST(:userId AS bigint))
                UNION ALL
                SELECT cmc.user_id, 'mission', CAST(cm.xp_reward AS numeric)
                FROM community_mission_contributions cmc
                JOIN user_missions cm ON cm.id = cmc.user_mission_id
                WHERE cmc.rewarded_at IS NOT NULL
                  AND (CAST(:userId AS bigint) IS NULL OR cmc.user_id = CAST(:userId AS bigint))
                UNION ALL
                SELECT uep.user_id, 'mission', CAST(uep.bonus_xp AS numeric)
                FROM user_event_profiles uep
                WHERE uep.bonus_awarded_at IS NOT NULL
                  AND (CAST(:userId AS bigint) IS NULL OR uep.user_id = CAST(:userId AS bigint))
            ),
            totals AS (
                SELECT user_id,
                    COALESCE(SUM(xp), 0) AS total_xp,
                    COALESCE(SUM(xp) FILTER (WHERE bucket = 'campaign'), 0) AS campaign_xp,
                    COALESCE(SUM(xp) FILTER (WHERE bucket = 'mission'), 0) AS mission_xp
                FROM sources
                GROUP BY user_id
            )
            UPDATE users u
            SET total_xp = COALESCE(t.total_xp, 0),
                campaign_xp = COALESCE(t.campaign_xp, 0),
                mission_xp = COALESCE(t.mission_xp, 0),
                updated_at = NOW()
            FROM users target
            LEFT JOIN totals t ON t.user_id = target.id
            WHERE u.id = target.id
              AND u.active = true
              AND (CAST(:userId AS bigint) IS NULL OR u.id = CAST(:userId AS bigint))
              AND (u.total_xp IS DISTINCT FROM COALESCE(t.total_xp, 0)
                OR u.campaign_xp IS DISTINCT FROM COALESCE(t.campaign_xp, 0)
                OR u.mission_xp IS DISTINCT FROM COALESCE(t.mission_xp, 0))
            """, nativeQuery = true)
    void rebuildXpTotals(@Param("userId") Long userId);

    @Query(value = """
            SELECT u.id
            FROM users u
            WHERE u.active = true
              AND u.total_xp >= :threshold
              AND NOT EXISTS (
                  SELECT 1 FROM user_item_links l
                  WHERE l.user_id = u.id
                    AND l.source = 'level'
                    AND l.source_id = CAST(:level AS text))
            ORDER BY u.id
            """, nativeQuery = true)
    List<Long> findUsersMissingLevelReward(@Param("level") int level, @Param("threshold") double threshold);

    @Query(value = """
            SELECT e.ts, e.xp FROM (
                SELECT COALESCE(s.time_set, s.created_at) AS ts, s.xp_gained AS xp
                FROM scores s WHERE s.user_id = :userId
                UNION ALL
                SELECT COALESCE(uml.completed_at, uml.created_at), m.xp
                FROM user_milestone_links uml
                JOIN milestones m ON uml.milestone_id = m.id
                WHERE uml.user_id = :userId AND uml.completed = true
                UNION ALL
                SELECT umsb.claimed_at, ms.set_bonus_xp
                FROM user_milestone_set_bonuses umsb
                JOIN milestone_sets ms ON umsb.milestone_set_id = ms.id
                WHERE umsb.user_id = :userId
                UNION ALL
                SELECT ucs.submitted_at, ucs.xp_awarded
                FROM user_campaign_scores ucs
                WHERE ucs.user_id = :userId AND ucs.active = true AND ucs.rewards_paid = true
                UNION ALL
                SELECT uc.completed_at, uc.completion_xp_awarded
                FROM user_campaigns uc
                WHERE uc.user_id = :userId AND uc.active = true AND uc.completion_rewards_paid = true
                UNION ALL
                SELECT um.completed_at, um.xp_reward
                FROM user_missions um
                WHERE um.user_id = :userId AND um.status = 'completed'
                UNION ALL
                SELECT cmc.rewarded_at, cm.xp_reward
                FROM community_mission_contributions cmc
                JOIN user_missions cm ON cm.id = cmc.user_mission_id
                WHERE cmc.user_id = :userId AND cmc.rewarded_at IS NOT NULL
                UNION ALL
                SELECT uep.bonus_awarded_at, uep.bonus_xp
                FROM user_event_profiles uep
                WHERE uep.user_id = :userId AND uep.bonus_awarded_at IS NOT NULL
            ) e
            WHERE e.xp IS NOT NULL AND e.xp <> 0
            ORDER BY e.ts ASC NULLS FIRST
            """, nativeQuery = true)
    List<Object[]> findXpTimeline(@Param("userId") Long userId);

    default void recalculateTotalXpForAllActiveUsers() {
        rebuildXpTotals(null);
    }

    default void recalculateTotalXpForUser(Long userId) {
        rebuildXpTotals(userId);
    }

    @Modifying
    @Transactional
    @Query(value = """
            WITH ranked AS (
                SELECT id, ROW_NUMBER() OVER (ORDER BY total_xp DESC) AS new_rank
                FROM users
                WHERE active = true AND banned = false AND total_xp > 0
            )
            UPDATE users u
            SET xp_ranking = r.new_rank, updated_at = NOW()
            FROM ranked r
            WHERE u.id = r.id AND u.xp_ranking IS DISTINCT FROM r.new_rank
            """, nativeQuery = true)
    void assignXpRankings();

    @Modifying
    @Transactional
    @Query(value = """
            WITH ranked AS (
                SELECT id, ROW_NUMBER() OVER (
                    PARTITION BY country ORDER BY total_xp DESC
                ) AS new_country_rank
                FROM users
                WHERE active = true AND banned = false AND total_xp > 0 AND country IS NOT NULL
            )
            UPDATE users u
            SET xp_country_ranking = r.new_country_rank, updated_at = NOW()
            FROM ranked r
            WHERE u.id = r.id AND u.xp_country_ranking IS DISTINCT FROM r.new_country_rank
            """, nativeQuery = true)
    void assignXpCountryRankings();
}
