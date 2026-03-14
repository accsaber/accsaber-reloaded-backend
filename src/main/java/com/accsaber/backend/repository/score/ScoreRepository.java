package com.accsaber.backend.repository.score;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.score.Score;

public interface ScoreRepository extends JpaRepository<Score, UUID> {

        Optional<Score> findByUser_IdAndMapDifficulty_IdAndActiveTrue(Long userId, UUID mapDifficultyId);

        List<Score> findByUser_IdAndMapDifficulty_IdInAndActiveTrue(Long userId, List<UUID> mapDifficultyIds);

        @Query("SELECT s FROM Score s JOIN FETCH s.user WHERE s.id = :id")
        Optional<Score> findByIdWithUser(@Param("id") UUID id);

        List<Score> findByMapDifficulty_IdAndActiveTrue(UUID mapDifficultyId);

        Page<Score> findByUser_IdAndActiveTrueOrderByApDesc(Long userId, Pageable pageable);

        Page<Score> findByMapDifficulty_IdAndActiveTrueOrderByScoreDesc(UUID mapDifficultyId, Pageable pageable);

        @Query("""
                        SELECT s FROM Score s
                        JOIN FETCH s.mapDifficulty d
                        WHERE s.user.id = :userId
                        AND d.category.id = :categoryId
                        AND s.active = true
                        ORDER BY s.ap DESC
                        """)
        List<Score> findActiveByUserAndCategoryOrderByApDesc(
                        @Param("userId") Long userId,
                        @Param("categoryId") UUID categoryId);

        @Query("""
                        SELECT s FROM Score s
                        WHERE s.user.id = :userId
                        AND s.mapDifficulty.category.id = :categoryId
                        AND s.active = true
                        ORDER BY s.ap DESC
                        """)
        Page<Score> findActiveByUserAndCategory(
                        @Param("userId") Long userId,
                        @Param("categoryId") UUID categoryId,
                        Pageable pageable);

        @Query("SELECT COALESCE(SUM(s.score), 0) FROM Score s WHERE s.user.id = :userId AND s.active = true")
        long sumActiveScoreByUser(@Param("userId") Long userId);

        @Query("""
                        SELECT COALESCE(SUM(s.score), 0) FROM Score s
                        WHERE s.user.id = :userId AND s.active = true
                        AND s.mapDifficulty.category.id = :categoryId
                        """)
        long sumActiveScoreByUserAndCategory(@Param("userId") Long userId, @Param("categoryId") UUID categoryId);

        @Query("""
                        SELECT COUNT(DISTINCT s.mapDifficulty.id) FROM Score s
                        WHERE s.user.id = :userId AND s.active = true
                        AND s.mapDifficulty.category.id = :categoryId
                        """)
        long countDistinctMapDifficultiesByUserAndCategory(
                        @Param("userId") Long userId, @Param("categoryId") UUID categoryId);

        @Query("""
                        SELECT COUNT(DISTINCT s.mapDifficulty.id) FROM Score s
                        WHERE s.user.id = :userId AND s.active = true
                        """)
        long countDistinctMapDifficultiesByUser(@Param("userId") Long userId);

        @Query("""
                        SELECT DISTINCT s.user.id FROM Score s
                        WHERE s.mapDifficulty.id = :mapDifficultyId AND s.active = true
                        """)
        List<Long> findDistinctUserIdsByMapDifficultyIdAndActiveTrue(
                        @Param("mapDifficultyId") UUID mapDifficultyId);

        List<Score> findByMapDifficulty_Id(UUID mapDifficultyId);

        @Query("SELECT COALESCE(SUM(s.xpGained), 0) FROM Score s WHERE s.user.id = :userId")
        java.math.BigDecimal sumXpGainedByUserId(@Param("userId") Long userId);

        @Query("SELECT DISTINCT s.mapDifficulty.id FROM Score s")
        List<UUID> findDistinctMapDifficultyIds();
}
