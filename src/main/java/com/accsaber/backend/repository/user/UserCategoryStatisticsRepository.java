package com.accsaber.backend.repository.user;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.user.UserCategoryStatistics;

public interface UserCategoryStatisticsRepository extends JpaRepository<UserCategoryStatistics, UUID> {

    Optional<UserCategoryStatistics> findByUser_IdAndCategory_IdAndActiveTrue(Long userId, UUID categoryId);

    Optional<UserCategoryStatistics> findByUser_IdAndCategory_CodeAndActiveTrue(Long userId, String categoryCode);

    @Query(value = """
            SELECT * FROM (
                SELECT ucs.* FROM user_category_statistics ucs
                JOIN categories c ON ucs.category_id = c.id
                WHERE ucs.user_id = :userId AND c.code = :categoryCode
                AND ucs.created_at > GREATEST(CAST(:since AS timestamptz), NOW() - INTERVAL '24 hours')
                UNION ALL
                SELECT ucs.* FROM user_category_statistics ucs
                INNER JOIN (
                    SELECT sub.id FROM (
                        SELECT id, ROW_NUMBER() OVER (
                            PARTITION BY date_trunc(
                                CASE WHEN CAST(:since AS timestamptz) < NOW() - INTERVAL '65 days'
                                    THEN 'week' ELSE 'day' END,
                                created_at
                            )
                            ORDER BY created_at DESC
                        ) AS rn
                        FROM user_category_statistics
                        WHERE user_id = :userId
                        AND category_id = (SELECT id FROM categories WHERE code = :categoryCode)
                        AND created_at > CAST(:since AS timestamptz)
                        AND created_at <= NOW() - INTERVAL '24 hours'
                    ) sub WHERE sub.rn = 1
                ) picked ON ucs.id = picked.id
            ) combined
            ORDER BY created_at ASC
            """, nativeQuery = true)
    List<UserCategoryStatistics> findHistoricDownsampled(
            @Param("userId") Long userId,
            @Param("categoryCode") String categoryCode,
            @Param("since") Instant since);

    @Query(value = """
            SELECT ucs.* FROM user_category_statistics ucs
            JOIN categories c ON ucs.category_id = c.id
            WHERE ucs.user_id = :userId AND c.code = :categoryCode
            AND ucs.created_at <= NOW() - INTERVAL '24 hours'
            ORDER BY ucs.created_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<UserCategoryStatistics> findLatestBeforeLastDay(
            @Param("userId") Long userId,
            @Param("categoryCode") String categoryCode);

    @Query(value = """
            SELECT ucs.* FROM user_category_statistics ucs
            JOIN categories c ON ucs.category_id = c.id
            WHERE ucs.user_id = :userId AND c.code = :categoryCode
            ORDER BY ucs.created_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<UserCategoryStatistics> findMostRecent(
            @Param("userId") Long userId,
            @Param("categoryCode") String categoryCode);

    List<UserCategoryStatistics> findByUser_IdAndActiveTrue(Long userId);

    @Query("""
            SELECT s FROM UserCategoryStatistics s
            JOIN FETCH s.user u
            WHERE s.category.id = :categoryId AND s.active = true AND u.active = true AND u.banned = false
            ORDER BY s.ap DESC
            """)
    List<UserCategoryStatistics> findActiveByCategoryOrderByApDesc(@Param("categoryId") UUID categoryId);

    @Query(value = """
            SELECT s FROM UserCategoryStatistics s
            JOIN FETCH s.user u
            WHERE s.category.id = :categoryId AND s.active = true AND u.active = true AND u.banned = false
            AND (:includeInactive = true OR u.playerInactive = false)
            AND (:hmd IS NULL OR EXISTS (SELECT 1 FROM Score sc WHERE sc.user = u AND sc.active = true AND sc.hmd = :hmd AND sc.timeSet = (SELECT MAX(sc2.timeSet) FROM Score sc2 WHERE sc2.user = u AND sc2.active = true)))
            """, countQuery = """
            SELECT COUNT(s) FROM UserCategoryStatistics s
            JOIN s.user u
            WHERE s.category.id = :categoryId AND s.active = true AND u.active = true AND u.banned = false
            AND (:includeInactive = true OR u.playerInactive = false)
            AND (:hmd IS NULL OR EXISTS (SELECT 1 FROM Score sc WHERE sc.user = u AND sc.active = true AND sc.hmd = :hmd AND sc.timeSet = (SELECT MAX(sc2.timeSet) FROM Score sc2 WHERE sc2.user = u AND sc2.active = true)))
            """)
    Page<UserCategoryStatistics> findActiveByCategoryPaged(
            @Param("categoryId") UUID categoryId,
            @Param("includeInactive") boolean includeInactive,
            @Param("hmd") String hmd,
            Pageable pageable);

    @Query(value = """
            SELECT s FROM UserCategoryStatistics s
            JOIN FETCH s.user u
            WHERE s.category.id = :categoryId AND s.active = true AND u.active = true AND u.banned = false
            AND LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%'))
            AND (:includeInactive = true OR u.playerInactive = false)
            AND (:hmd IS NULL OR EXISTS (SELECT 1 FROM Score sc WHERE sc.user = u AND sc.active = true AND sc.hmd = :hmd AND sc.timeSet = (SELECT MAX(sc2.timeSet) FROM Score sc2 WHERE sc2.user = u AND sc2.active = true)))
            """, countQuery = """
            SELECT COUNT(s) FROM UserCategoryStatistics s
            JOIN s.user u
            WHERE s.category.id = :categoryId AND s.active = true AND u.active = true AND u.banned = false
            AND LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%'))
            AND (:includeInactive = true OR u.playerInactive = false)
            AND (:hmd IS NULL OR EXISTS (SELECT 1 FROM Score sc WHERE sc.user = u AND sc.active = true AND sc.hmd = :hmd AND sc.timeSet = (SELECT MAX(sc2.timeSet) FROM Score sc2 WHERE sc2.user = u AND sc2.active = true)))
            """)
    Page<UserCategoryStatistics> findActiveByCategoryPagedWithSearch(
            @Param("categoryId") UUID categoryId,
            @Param("search") String search,
            @Param("includeInactive") boolean includeInactive,
            @Param("hmd") String hmd,
            Pageable pageable);

    @Query(value = """
            SELECT s FROM UserCategoryStatistics s
            JOIN FETCH s.user u
            WHERE s.category.id = :categoryId AND s.active = true AND u.active = true AND u.banned = false
            AND LOWER(u.country) = LOWER(:country)
            AND (:includeInactive = true OR u.playerInactive = false)
            AND (:hmd IS NULL OR EXISTS (SELECT 1 FROM Score sc WHERE sc.user = u AND sc.active = true AND sc.hmd = :hmd AND sc.timeSet = (SELECT MAX(sc2.timeSet) FROM Score sc2 WHERE sc2.user = u AND sc2.active = true)))
            """, countQuery = """
            SELECT COUNT(s) FROM UserCategoryStatistics s
            JOIN s.user u
            WHERE s.category.id = :categoryId AND s.active = true AND u.active = true AND u.banned = false
            AND LOWER(u.country) = LOWER(:country)
            AND (:includeInactive = true OR u.playerInactive = false)
            AND (:hmd IS NULL OR EXISTS (SELECT 1 FROM Score sc WHERE sc.user = u AND sc.active = true AND sc.hmd = :hmd AND sc.timeSet = (SELECT MAX(sc2.timeSet) FROM Score sc2 WHERE sc2.user = u AND sc2.active = true)))
            """)
    Page<UserCategoryStatistics> findActiveByCategoryAndCountryPaged(
            @Param("categoryId") UUID categoryId,
            @Param("country") String country,
            @Param("includeInactive") boolean includeInactive,
            @Param("hmd") String hmd,
            Pageable pageable);

    @Query(value = """
            SELECT s FROM UserCategoryStatistics s
            JOIN FETCH s.user u
            WHERE s.category.id = :categoryId AND s.active = true AND u.active = true AND u.banned = false
            AND LOWER(u.country) = LOWER(:country)
            AND LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%'))
            AND (:includeInactive = true OR u.playerInactive = false)
            AND (:hmd IS NULL OR EXISTS (SELECT 1 FROM Score sc WHERE sc.user = u AND sc.active = true AND sc.hmd = :hmd AND sc.timeSet = (SELECT MAX(sc2.timeSet) FROM Score sc2 WHERE sc2.user = u AND sc2.active = true)))
            """, countQuery = """
            SELECT COUNT(s) FROM UserCategoryStatistics s
            JOIN s.user u
            WHERE s.category.id = :categoryId AND s.active = true AND u.active = true AND u.banned = false
            AND LOWER(u.country) = LOWER(:country)
            AND LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%'))
            AND (:includeInactive = true OR u.playerInactive = false)
            AND (:hmd IS NULL OR EXISTS (SELECT 1 FROM Score sc WHERE sc.user = u AND sc.active = true AND sc.hmd = :hmd AND sc.timeSet = (SELECT MAX(sc2.timeSet) FROM Score sc2 WHERE sc2.user = u AND sc2.active = true)))
            """)
    Page<UserCategoryStatistics> findActiveByCategoryAndCountryPagedWithSearch(
            @Param("categoryId") UUID categoryId,
            @Param("country") String country,
            @Param("search") String search,
            @Param("includeInactive") boolean includeInactive,
            @Param("hmd") String hmd,
            Pageable pageable);

    @Query("""
            SELECT s FROM UserCategoryStatistics s
            JOIN FETCH s.category c
            WHERE s.user.id = :userId AND s.active = true AND c.countForOverall = true
            """)
    List<UserCategoryStatistics> findActiveByUserWhereCountForOverall(@Param("userId") Long userId);

    @Modifying
    @Query(value = """
            WITH ranked AS (
                SELECT ucs.id, ROW_NUMBER() OVER (
                    ORDER BY ucs.ap DESC, sp.time_set ASC NULLS LAST
                ) AS new_rank
                FROM user_category_statistics ucs
                JOIN users u ON ucs.user_id = u.id
                LEFT JOIN scores sp ON sp.id = ucs.top_play_id
                WHERE ucs.category_id = :categoryId AND ucs.active = true AND u.active = true AND u.banned = false
            )
            UPDATE user_category_statistics ucs
            SET ranking = r.new_rank, updated_at = NOW()
            FROM ranked r
            WHERE ucs.id = r.id AND ucs.ranking IS DISTINCT FROM r.new_rank
            """, nativeQuery = true)
    void assignGlobalRankings(@Param("categoryId") UUID categoryId);

    @Modifying
    @Query(value = """
            WITH ranked AS (
                SELECT ucs.id,
                    ROW_NUMBER() OVER (
                        PARTITION BY u.country
                        ORDER BY ucs.ap DESC, sp.time_set ASC NULLS LAST
                    ) AS new_country_rank
                FROM user_category_statistics ucs
                JOIN users u ON ucs.user_id = u.id
                LEFT JOIN scores sp ON sp.id = ucs.top_play_id
                WHERE ucs.category_id = :categoryId AND ucs.active = true AND u.active = true AND u.banned = false AND u.country IS NOT NULL
            )
            UPDATE user_category_statistics ucs
            SET country_ranking = r.new_country_rank, updated_at = NOW()
            FROM ranked r
            WHERE ucs.id = r.id AND ucs.country_ranking IS DISTINCT FROM r.new_country_rank
            """, nativeQuery = true)
    void assignCountryRankings(@Param("categoryId") UUID categoryId);

    @Query(value = """
            SELECT DISTINCT ON (ucs.user_id) ucs.user_id, ucs.ranking
            FROM user_category_statistics ucs
            WHERE ucs.category_id = :categoryId
            AND ucs.user_id IN :userIds
            AND ucs.created_at <= NOW() - INTERVAL '7 days'
            ORDER BY ucs.user_id, ucs.created_at DESC
            """, nativeQuery = true)
    List<Object[]> findRankingsOneWeekAgo(@Param("categoryId") UUID categoryId,
            @Param("userIds") List<Long> userIds);
}
