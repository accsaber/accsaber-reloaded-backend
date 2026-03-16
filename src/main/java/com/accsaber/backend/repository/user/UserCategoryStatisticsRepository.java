package com.accsaber.backend.repository.user;

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

        List<UserCategoryStatistics> findByUser_IdAndActiveTrue(Long userId);

        @Query("""
                        SELECT s FROM UserCategoryStatistics s
                        JOIN FETCH s.user u
                        WHERE s.category.id = :categoryId AND s.active = true AND u.active = true
                        ORDER BY s.ap DESC
                        """)
        List<UserCategoryStatistics> findActiveByCategoryOrderByApDesc(@Param("categoryId") UUID categoryId);

        @Query(value = """
                        SELECT s FROM UserCategoryStatistics s
                        JOIN FETCH s.user u
                        WHERE s.category.id = :categoryId AND s.active = true AND u.active = true
                        """, countQuery = """
                        SELECT COUNT(s) FROM UserCategoryStatistics s
                        JOIN s.user u
                        WHERE s.category.id = :categoryId AND s.active = true AND u.active = true
                        """)
        Page<UserCategoryStatistics> findActiveByCategoryPaged(
                        @Param("categoryId") UUID categoryId, Pageable pageable);

        @Query(value = """
                        SELECT s FROM UserCategoryStatistics s
                        JOIN FETCH s.user u
                        WHERE s.category.id = :categoryId AND s.active = true AND u.active = true
                          AND LOWER(u.country) = LOWER(:country)
                        """, countQuery = """
                        SELECT COUNT(s) FROM UserCategoryStatistics s
                        JOIN s.user u
                        WHERE s.category.id = :categoryId AND s.active = true AND u.active = true
                          AND LOWER(u.country) = LOWER(:country)
                        """)
        Page<UserCategoryStatistics> findActiveByCategoryAndCountryPaged(
                        @Param("categoryId") UUID categoryId,
                        @Param("country") String country,
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
                            SELECT ucs.id, ROW_NUMBER() OVER (ORDER BY ucs.ap DESC) AS new_rank
                            FROM user_category_statistics ucs
                            JOIN users u ON ucs.user_id = u.id
                            WHERE ucs.category_id = :categoryId AND ucs.active = true AND u.active = true
                        )
                        UPDATE user_category_statistics ucs
                        SET ranking = r.new_rank, updated_at = NOW()
                        FROM ranked r
                        WHERE ucs.id = r.id
                        """, nativeQuery = true)
        void assignGlobalRankings(@Param("categoryId") UUID categoryId);

        @Modifying
        @Query(value = """
                        WITH ranked AS (
                            SELECT ucs.id,
                                   ROW_NUMBER() OVER (PARTITION BY u.country ORDER BY ucs.ap DESC) AS new_country_rank
                            FROM user_category_statistics ucs
                            JOIN users u ON ucs.user_id = u.id
                            WHERE ucs.category_id = :categoryId AND ucs.active = true AND u.active = true AND u.country IS NOT NULL
                        )
                        UPDATE user_category_statistics ucs
                        SET country_ranking = r.new_country_rank, updated_at = NOW()
                        FROM ranked r
                        WHERE ucs.id = r.id
                        """, nativeQuery = true)
        void assignCountryRankings(@Param("categoryId") UUID categoryId);
}
