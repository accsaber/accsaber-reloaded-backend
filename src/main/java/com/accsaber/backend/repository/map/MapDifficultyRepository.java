package com.accsaber.backend.repository.map;

import java.math.BigDecimal;
import java.util.Collection;
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
                        WHERE d.id IN :ids AND d.active = true
                        """)
        List<MapDifficulty> findAllByIdInAndActiveTrueWithCategory(@Param("ids") List<UUID> ids);

        @Query("""
                        SELECT d FROM MapDifficulty d
                        JOIN FETCH d.map
                        LEFT JOIN FETCH d.category
                        WHERE d.id IN :ids AND d.active = true
                        """)
        List<MapDifficulty> findAllByIdInAndActiveTrueWithMapAndCategory(@Param("ids") List<UUID> ids);

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

        @Query("""
                        SELECT d FROM MapDifficulty d
                        WHERE d.blLeaderboardId = CAST(:leaderboardId AS string)
                           OR d.id IN (
                                SELECT a.mapDifficulty.id FROM MapDifficultyLeaderboardAlias a
                                WHERE a.blLeaderboardId = CAST(:leaderboardId AS string))
                        """)
        Optional<MapDifficulty> findByBlLeaderboardId(@Param("leaderboardId") String leaderboardId);

        @Query("""
                        SELECT d FROM MapDifficulty d
                        WHERE d.ssLeaderboardId = CAST(:leaderboardId AS string)
                           OR d.id IN (
                                SELECT a.mapDifficulty.id FROM MapDifficultyLeaderboardAlias a
                                WHERE a.ssLeaderboardId = CAST(:leaderboardId AS string))
                        """)
        Optional<MapDifficulty> findBySsLeaderboardId(@Param("leaderboardId") String leaderboardId);

        List<MapDifficulty> findByActiveFalseOrderByUpdatedAtDesc();

        List<MapDifficulty> findByStatusAndActiveTrue(MapDifficultyStatus status);

        @Query("""
                        SELECT d FROM MapDifficulty d
                        WHERE d.map.id = :mapId AND d.active = true
                        AND (:categoryId IS NULL OR d.category.id = :categoryId)
                        AND ((:statuses IS NULL AND d.status <> com.accsaber.backend.model.entity.map.MapDifficultyStatus.CAMPAIGN)
                             OR d.status IN :statuses)
                        """)
        List<MapDifficulty> findByMapIdWithFilters(
                        @Param("mapId") UUID mapId,
                        @Param("categoryId") UUID categoryId,
                        @Param("status") MapDifficultyStatus status);

        @Query("""
                        SELECT d.blLeaderboardId FROM MapDifficulty d
                        WHERE d.map.id = :mapId AND d.active = true AND d.blLeaderboardId IS NOT NULL
                        ORDER BY d.blLeaderboardId ASC
                        """)
        List<String> findBlLeaderboardIdsByMapId(@Param("mapId") UUID mapId);

        @Query("""
                        SELECT d FROM MapDifficulty d
                        WHERE d.map.id IN :mapIds AND d.active = true
                        AND (:categoryId IS NULL OR d.category.id = :categoryId)
                        AND ((:statuses IS NULL AND d.status <> com.accsaber.backend.model.entity.map.MapDifficultyStatus.CAMPAIGN)
                             OR d.status IN :statuses)
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

        @Query("""
                        SELECT d FROM MapDifficulty d
                        JOIN FETCH d.map m
                        WHERE d.category.id = :categoryId AND d.status IN :statuses AND d.active = true
                        ORDER BY d.createdAt DESC
                        """)
        List<MapDifficulty> findByCategoryIdAndStatusInWithMap(
                        @Param("categoryId") UUID categoryId,
                        @Param("statuses") List<MapDifficultyStatus> statuses);

        @Query("""
                        SELECT d FROM MapDifficulty d
                        JOIN FETCH d.map m
                        WHERE d.category.countForOverall = true AND d.status IN :statuses AND d.active = true
                        ORDER BY d.createdAt DESC
                        """)
        List<MapDifficulty> findByCountForOverallAndStatusInWithMap(
                        @Param("statuses") List<MapDifficultyStatus> statuses);

        @Query("""
                        SELECT d.id, d.map.songHash, d.map.songName, d.difficulty, c.complexity, d.category.code, d.ssLeaderboardId, d.blLeaderboardId
                        FROM MapDifficulty d
                        LEFT JOIN MapDifficultyComplexity c ON c.mapDifficulty = d AND c.active = true
                        WHERE d.status = com.accsaber.backend.model.entity.map.MapDifficultyStatus.RANKED
                        AND d.active = true
                        ORDER BY d.map.songHash, d.difficulty
                        """)
        List<Object[]> findAllRankedWithComplexity();

        @Query("""
                        SELECT d, c.complexity FROM MapDifficulty d
                        JOIN MapDifficultyComplexity c ON c.mapDifficulty = d AND c.active = true
                        JOIN FETCH d.map
                        JOIN FETCH d.category cat
                        LEFT JOIN FETCH cat.scoreCurve
                        WHERE d.status = com.accsaber.backend.model.entity.map.MapDifficultyStatus.RANKED
                          AND d.active = true
                          AND (:categoryId IS NULL OR cat.id = :categoryId)
                          AND c.complexity BETWEEN :minComplexity AND :maxComplexity
                        ORDER BY d.id
                        """)
        List<Object[]> findRankedWithComplexityInRange(
                        @Param("categoryId") UUID categoryId,
                        @Param("minComplexity") BigDecimal minComplexity,
                        @Param("maxComplexity") BigDecimal maxComplexity);

        @Query(value = """
                        SELECT d FROM MapDifficulty d
                        LEFT JOIN MapDifficultyComplexity c ON c.mapDifficulty = d AND c.active = true
                        LEFT JOIN MapDifficultyStatistics mds ON mds.mapDifficulty = d AND mds.active = true
                        WHERE d.active = true
                        AND (:categoryId IS NULL OR d.category.id = :categoryId)
                        AND (:batchId IS NULL OR d.batch.id = :batchId)
                        AND ((:statuses IS NULL AND d.status <> com.accsaber.backend.model.entity.map.MapDifficultyStatus.CAMPAIGN)
                             OR d.status IN :statuses)
                        AND (:complexityMin IS NULL OR c.complexity >= :complexityMin)
                        AND (:complexityMax IS NULL OR c.complexity <= :complexityMax)
                        AND (:excludeUserId IS NULL OR NOT EXISTS (
                                SELECT 1 FROM Score s
                                WHERE s.mapDifficulty = d AND s.user.id = :excludeUserId AND s.active = true))
                        """, countQuery = """
                        SELECT COUNT(d) FROM MapDifficulty d
                        LEFT JOIN MapDifficultyComplexity c ON c.mapDifficulty = d AND c.active = true
                        LEFT JOIN MapDifficultyStatistics mds ON mds.mapDifficulty = d AND mds.active = true
                        WHERE d.active = true
                        AND (:categoryId IS NULL OR d.category.id = :categoryId)
                        AND (:batchId IS NULL OR d.batch.id = :batchId)
                        AND ((:statuses IS NULL AND d.status <> com.accsaber.backend.model.entity.map.MapDifficultyStatus.CAMPAIGN)
                             OR d.status IN :statuses)
                        AND (:complexityMin IS NULL OR c.complexity >= :complexityMin)
                        AND (:complexityMax IS NULL OR c.complexity <= :complexityMax)
                        AND (:excludeUserId IS NULL OR NOT EXISTS (
                                SELECT 1 FROM Score s
                                WHERE s.mapDifficulty = d AND s.user.id = :excludeUserId AND s.active = true))
                        """)
        Page<MapDifficulty> findWithComplexityFilters(
                        @Param("categoryId") UUID categoryId,
                        @Param("batchId") UUID batchId,
                        @Param("statuses") Collection<MapDifficultyStatus> statuses,
                        @Param("complexityMin") BigDecimal complexityMin,
                        @Param("complexityMax") BigDecimal complexityMax,
                        @Param("excludeUserId") Long excludeUserId,
                        Pageable pageable);

        @Query(value = """
                        SELECT d FROM MapDifficulty d
                        LEFT JOIN MapDifficultyComplexity c ON c.mapDifficulty = d AND c.active = true
                        LEFT JOIN MapDifficultyStatistics mds ON mds.mapDifficulty = d AND mds.active = true
                        WHERE d.active = true
                        AND (:categoryId IS NULL OR d.category.id = :categoryId)
                        AND (:batchId IS NULL OR d.batch.id = :batchId)
                        AND ((:statuses IS NULL AND d.status <> com.accsaber.backend.model.entity.map.MapDifficultyStatus.CAMPAIGN)
                             OR d.status IN :statuses)
                        AND (:complexityMin IS NULL OR c.complexity >= :complexityMin)
                        AND (:complexityMax IS NULL OR c.complexity <= :complexityMax)
                        AND (:excludeUserId IS NULL OR NOT EXISTS (
                                SELECT 1 FROM Score s
                                WHERE s.mapDifficulty = d AND s.user.id = :excludeUserId AND s.active = true))
                        AND (LOWER(d.map.songName) LIKE LOWER(CONCAT('%', :search, '%'))
                        OR LOWER(d.map.songAuthor) LIKE LOWER(CONCAT('%', :search, '%'))
                        OR LOWER(d.map.mapAuthor) LIKE LOWER(CONCAT('%', :search, '%')))
                        """, countQuery = """
                        SELECT COUNT(d) FROM MapDifficulty d
                        LEFT JOIN MapDifficultyComplexity c ON c.mapDifficulty = d AND c.active = true
                        LEFT JOIN MapDifficultyStatistics mds ON mds.mapDifficulty = d AND mds.active = true
                        WHERE d.active = true
                        AND (:categoryId IS NULL OR d.category.id = :categoryId)
                        AND (:batchId IS NULL OR d.batch.id = :batchId)
                        AND ((:statuses IS NULL AND d.status <> com.accsaber.backend.model.entity.map.MapDifficultyStatus.CAMPAIGN)
                             OR d.status IN :statuses)
                        AND (:complexityMin IS NULL OR c.complexity >= :complexityMin)
                        AND (:complexityMax IS NULL OR c.complexity <= :complexityMax)
                        AND (:excludeUserId IS NULL OR NOT EXISTS (
                                SELECT 1 FROM Score s
                                WHERE s.mapDifficulty = d AND s.user.id = :excludeUserId AND s.active = true))
                        AND (LOWER(d.map.songName) LIKE LOWER(CONCAT('%', :search, '%'))
                        OR LOWER(d.map.songAuthor) LIKE LOWER(CONCAT('%', :search, '%'))
                        OR LOWER(d.map.mapAuthor) LIKE LOWER(CONCAT('%', :search, '%')))
                        """)
        Page<MapDifficulty> findWithComplexityFiltersWithSearch(
                        @Param("categoryId") UUID categoryId,
                        @Param("batchId") UUID batchId,
                        @Param("statuses") Collection<MapDifficultyStatus> statuses,
                        @Param("complexityMin") BigDecimal complexityMin,
                        @Param("complexityMax") BigDecimal complexityMax,
                        @Param("excludeUserId") Long excludeUserId,
                        @Param("search") String search,
                        Pageable pageable);

        @Query("""
                        SELECT d FROM MapDifficulty d
                        JOIN FETCH d.map
                        WHERE d.active = true
                        AND (:categoryId IS NULL OR d.category.id = :categoryId)
                        AND EXISTS (
                                SELECT 1 FROM Score s
                                WHERE s.mapDifficulty = d AND s.user.id = :userId AND s.active = true
                                AND s.ap >= :apMin)
                        """)
        List<MapDifficulty> findWithUserScoreAboveAp(
                        @Param("userId") Long userId,
                        @Param("apMin") BigDecimal apMin,
                        @Param("categoryId") UUID categoryId);

        long countByImportedByAndStatusAndActiveTrue(Long importedBy, MapDifficultyStatus status);

        boolean existsByIdAndBatch_Id(UUID id, UUID batchId);

        boolean existsByIdAndRankedAtBefore(UUID id, java.time.Instant rankedBefore);
}
