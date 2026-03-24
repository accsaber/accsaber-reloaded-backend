package com.accsaber.backend.repository.map;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;

public interface MapDifficultyRepository extends JpaRepository<MapDifficulty, UUID> {

        Optional<MapDifficulty> findByIdAndActiveTrue(UUID id);

        @Query("""
                        SELECT d FROM MapDifficulty d
                        JOIN FETCH d.map m
                        WHERE d.category.id = :categoryId AND d.status = :status AND d.active = true
                        ORDER BY d.rankedAt DESC
                        """)
        List<MapDifficulty> findByCategoryIdAndStatusWithMap(
                        @Param("categoryId") UUID categoryId,
                        @Param("status") MapDifficultyStatus status);

        @Query("""
                        SELECT d FROM MapDifficulty d
                        JOIN FETCH d.category c
                        JOIN FETCH c.scoreCurve
                        JOIN FETCH c.weightCurve
                        WHERE d.id = :id AND d.active = true
                        """)
        Optional<MapDifficulty> findByIdAndActiveTrueWithCategory(@Param("id") UUID id);

        @Query("""
                        SELECT d FROM MapDifficulty d
                        JOIN FETCH d.category c
                        JOIN FETCH c.scoreCurve
                        JOIN FETCH c.weightCurve
                        WHERE d.status = :status AND d.active = true
                        """)
        List<MapDifficulty> findByStatusAndActiveTrueWithCategory(@Param("status") MapDifficultyStatus status);

        @Query("""
                        SELECT d FROM MapDifficulty d
                        JOIN FETCH d.category c
                        JOIN FETCH c.scoreCurve
                        JOIN FETCH c.weightCurve
                        WHERE d.category.id = :categoryId AND d.status = :status AND d.active = true
                        """)
        List<MapDifficulty> findByCategoryIdAndStatusAndActiveTrueWithCategory(
                        @Param("categoryId") UUID categoryId,
                        @Param("status") MapDifficultyStatus status);

        List<MapDifficulty> findByMapIdAndActiveTrue(UUID mapId);

        List<MapDifficulty> findByMapIdInAndActiveTrue(List<UUID> mapIds);

        Optional<MapDifficulty> findByMapIdAndDifficultyAndCharacteristicAndActiveTrue(
                        UUID mapId, Difficulty difficulty, String characteristic);

        List<MapDifficulty> findByBatch_IdAndActiveTrue(UUID batchId);

        @Query("""
                        SELECT d FROM MapDifficulty d
                        JOIN FETCH d.category c
                        JOIN FETCH c.scoreCurve
                        JOIN FETCH c.weightCurve
                        WHERE d.batch.id = :batchId AND d.active = true
                        """)
        List<MapDifficulty> findByBatchIdAndActiveTrueWithCategory(@Param("batchId") UUID batchId);

        Optional<MapDifficulty> findByBlLeaderboardId(String blLeaderboardId);

        Optional<MapDifficulty> findBySsLeaderboardId(String ssLeaderboardId);

        List<MapDifficulty> findByActiveFalseOrderByUpdatedAtDesc();

        List<MapDifficulty> findByStatusAndActiveTrue(MapDifficultyStatus status);

        @Query("""
                        SELECT d FROM MapDifficulty d
                        WHERE d.map.id = :mapId AND d.active = true
                        AND (:categoryId IS NULL OR d.category.id = :categoryId)
                        AND (:status IS NULL OR d.status = :status)
                        """)
        List<MapDifficulty> findByMapIdWithFilters(
                        @Param("mapId") UUID mapId,
                        @Param("categoryId") UUID categoryId,
                        @Param("status") MapDifficultyStatus status);

        @Query("""
                        SELECT d FROM MapDifficulty d
                        WHERE d.map.id IN :mapIds AND d.active = true
                        AND (:categoryId IS NULL OR d.category.id = :categoryId)
                        AND (:status IS NULL OR d.status = :status)
                        """)
        List<MapDifficulty> findByMapIdsWithFilters(
                        @Param("mapIds") List<UUID> mapIds,
                        @Param("categoryId") UUID categoryId,
                        @Param("status") MapDifficultyStatus status);

        @Query("""
                        SELECT COUNT(d) FROM MapDifficulty d
                        WHERE d.category.id = :categoryId AND d.status = :status AND d.active = true
                        """)
        long countByCategoryIdAndStatus(@Param("categoryId") UUID categoryId,
                        @Param("status") MapDifficultyStatus status);

        @Query("""
                        SELECT d FROM MapDifficulty d
                        JOIN FETCH d.map m
                        WHERE d.category.countForOverall = true AND d.status = :status AND d.active = true
                        ORDER BY d.rankedAt DESC
                        """)
        List<MapDifficulty> findByCountForOverallAndStatusWithMap(
                        @Param("status") MapDifficultyStatus status);

        @Query(value = """
                        SELECT d FROM MapDifficulty d
                        LEFT JOIN MapDifficultyComplexity c ON c.mapDifficulty = d AND c.active = true
                        LEFT JOIN MapDifficultyStatistics mds ON mds.mapDifficulty = d AND mds.active = true
                        WHERE d.active = true
                        AND (:categoryId IS NULL OR d.category.id = :categoryId)
                        AND (:status IS NULL OR d.status = :status)
                        AND (:complexityMin IS NULL OR c.complexity >= :complexityMin)
                        AND (:complexityMax IS NULL OR c.complexity <= :complexityMax)
                        """, countQuery = """
                        SELECT COUNT(d) FROM MapDifficulty d
                        LEFT JOIN MapDifficultyComplexity c ON c.mapDifficulty = d AND c.active = true
                        LEFT JOIN MapDifficultyStatistics mds ON mds.mapDifficulty = d AND mds.active = true
                        WHERE d.active = true
                        AND (:categoryId IS NULL OR d.category.id = :categoryId)
                        AND (:status IS NULL OR d.status = :status)
                        AND (:complexityMin IS NULL OR c.complexity >= :complexityMin)
                        AND (:complexityMax IS NULL OR c.complexity <= :complexityMax)
                        """)
        Page<MapDifficulty> findWithComplexityFilters(
                        @Param("categoryId") UUID categoryId,
                        @Param("status") MapDifficultyStatus status,
                        @Param("complexityMin") BigDecimal complexityMin,
                        @Param("complexityMax") BigDecimal complexityMax,
                        Pageable pageable);

        @Query(value = """
                        SELECT d FROM MapDifficulty d
                        LEFT JOIN MapDifficultyComplexity c ON c.mapDifficulty = d AND c.active = true
                        LEFT JOIN MapDifficultyStatistics mds ON mds.mapDifficulty = d AND mds.active = true
                        WHERE d.active = true
                        AND (:categoryId IS NULL OR d.category.id = :categoryId)
                        AND (:status IS NULL OR d.status = :status)
                        AND (:complexityMin IS NULL OR c.complexity >= :complexityMin)
                        AND (:complexityMax IS NULL OR c.complexity <= :complexityMax)
                        AND LOWER(d.map.songName) LIKE LOWER(CONCAT('%', :search, '%'))
                        """, countQuery = """
                        SELECT COUNT(d) FROM MapDifficulty d
                        LEFT JOIN MapDifficultyComplexity c ON c.mapDifficulty = d AND c.active = true
                        LEFT JOIN MapDifficultyStatistics mds ON mds.mapDifficulty = d AND mds.active = true
                        WHERE d.active = true
                        AND (:categoryId IS NULL OR d.category.id = :categoryId)
                        AND (:status IS NULL OR d.status = :status)
                        AND (:complexityMin IS NULL OR c.complexity >= :complexityMin)
                        AND (:complexityMax IS NULL OR c.complexity <= :complexityMax)
                        AND LOWER(d.map.songName) LIKE LOWER(CONCAT('%', :search, '%'))
                        """)
        Page<MapDifficulty> findWithComplexityFiltersWithSearch(
                        @Param("categoryId") UUID categoryId,
                        @Param("status") MapDifficultyStatus status,
                        @Param("complexityMin") BigDecimal complexityMin,
                        @Param("complexityMax") BigDecimal complexityMax,
                        @Param("search") String search,
                        Pageable pageable);
}
