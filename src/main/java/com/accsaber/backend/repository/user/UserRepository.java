package com.accsaber.backend.repository.user;

import java.math.BigDecimal;
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
            AND LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%'))
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
            AND LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%'))
            AND (:includeInactive = true OR u.playerInactive = false)
            AND (:hmd IS NULL OR EXISTS (SELECT 1 FROM Score sc WHERE sc.user = u AND sc.active = true AND sc.hmd = :hmd AND NOT EXISTS (SELECT 1 FROM Score sc3 WHERE sc3.user = u AND sc3.active = true AND sc3.timeSet > sc.timeSet)))
            ORDER BY u.totalXp DESC
            """)
    Page<User> findXpLeaderboardByCountryWithSearch(@Param("country") String country,
            @Param("search") String search, @Param("includeInactive") boolean includeInactive,
            @Param("hmd") String hmd, Pageable pageable);

    @Query("""
            SELECT u FROM User u
            WHERE u.active = true AND u.banned = false AND u.totalXp > 0
            AND u.id IN :userIds
            AND (CAST(:country AS string) IS NULL OR LOWER(u.country) = LOWER(CAST(:country AS string)))
            AND (CAST(:search AS string) IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
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
    void addXp(@Param("id") Long id, @Param("xp") BigDecimal xp);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.missionXp = u.missionXp + :xp WHERE u.id = :id")
    void addMissionXp(@Param("id") Long id, @Param("xp") BigDecimal xp);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.campaignXp = u.campaignXp + :xp WHERE u.id = :id")
    void addCampaignXp(@Param("id") Long id, @Param("xp") BigDecimal xp);

    @Query("SELECT u.totalXp FROM User u WHERE u.id = :id")
    java.util.Optional<BigDecimal> findTotalXpById(@Param("id") Long id);

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
            UPDATE users u SET total_xp =
                COALESCE(sx.score_xp, 0)
                + COALESCE(mx.milestone_xp, 0)
                + COALESCE(bx.bonus_xp, 0)
                + COALESCE(cx.campaign_difficulty_xp, 0)
                + COALESCE(cmx.campaign_completion_xp, 0)
                + COALESCE(u.mission_xp, 0),
            campaign_xp = COALESCE(cx.campaign_difficulty_xp, 0) + COALESCE(cmx.campaign_completion_xp, 0),
            updated_at = NOW()
            FROM (
                SELECT user_id, SUM(xp_gained) AS score_xp FROM scores GROUP BY user_id
            ) sx
            FULL OUTER JOIN (
                SELECT uml.user_id, SUM(m.xp) AS milestone_xp
                FROM user_milestone_links uml JOIN milestones m ON uml.milestone_id = m.id
                WHERE uml.completed = true
                GROUP BY uml.user_id
            ) mx ON mx.user_id = sx.user_id
            FULL OUTER JOIN (
                SELECT umsb.user_id, SUM(ms.set_bonus_xp) AS bonus_xp
                FROM user_milestone_set_bonuses umsb JOIN milestone_sets ms ON umsb.milestone_set_id = ms.id
                GROUP BY umsb.user_id
            ) bx ON bx.user_id = COALESCE(sx.user_id, mx.user_id)
            FULL OUTER JOIN (
                SELECT ucs.user_id, SUM(cd.xp) AS campaign_difficulty_xp
                FROM user_campaign_scores ucs
                JOIN campaign_difficulties cd ON ucs.campaign_difficulty_id = cd.id
                JOIN campaigns c ON ucs.campaign_id = c.id
                WHERE ucs.active = true AND c.status = 'curated' AND cd.active = true
                GROUP BY ucs.user_id
            ) cx ON cx.user_id = COALESCE(sx.user_id, mx.user_id, bx.user_id)
            FULL OUTER JOIN (
                SELECT uc.user_id, SUM(c.completion_xp) AS campaign_completion_xp
                FROM user_campaigns uc
                JOIN campaigns c ON uc.campaign_id = c.id
                WHERE uc.active = true AND uc.status = 'completed' AND c.status = 'curated'
                GROUP BY uc.user_id
            ) cmx ON cmx.user_id = COALESCE(sx.user_id, mx.user_id, bx.user_id, cx.user_id)
            WHERE u.id = COALESCE(sx.user_id, mx.user_id, bx.user_id, cx.user_id, cmx.user_id)
            AND u.active = true
            """, nativeQuery = true)
    void recalculateTotalXpForAllActiveUsers();

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
