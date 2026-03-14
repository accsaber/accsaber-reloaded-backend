package com.accsaber.backend.repository.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.user.UserCategoryStatistics;

public interface UserCategoryStatisticsRepository extends JpaRepository<UserCategoryStatistics, UUID> {

        Optional<UserCategoryStatistics> findByUser_IdAndCategory_IdAndActiveTrue(Long userId, UUID categoryId);

        List<UserCategoryStatistics> findByUser_IdAndActiveTrue(Long userId);

        @Query("""
                        SELECT s FROM UserCategoryStatistics s
                        JOIN FETCH s.user u
                        WHERE s.category.id = :categoryId AND s.active = true
                        ORDER BY s.ap DESC
                        """)
        List<UserCategoryStatistics> findActiveByCategoryOrderByApDesc(@Param("categoryId") UUID categoryId);

        @Query("""
                        SELECT s FROM UserCategoryStatistics s
                        JOIN FETCH s.category c
                        WHERE s.user.id = :userId AND s.active = true AND c.countForOverall = true
                        """)
        List<UserCategoryStatistics> findActiveByUserWhereCountForOverall(@Param("userId") Long userId);
}
