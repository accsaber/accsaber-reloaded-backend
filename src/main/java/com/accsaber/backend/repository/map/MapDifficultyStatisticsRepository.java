package com.accsaber.backend.repository.map;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.map.MapDifficultyStatistics;

public interface MapDifficultyStatisticsRepository extends JpaRepository<MapDifficultyStatistics, UUID> {

    Optional<MapDifficultyStatistics> findByMapDifficultyIdAndActiveTrue(UUID mapDifficultyId);

    @Query("""
            SELECT s FROM MapDifficultyStatistics s
            WHERE s.mapDifficulty.id IN :difficultyIds AND s.active = true
            """)
    List<MapDifficultyStatistics> findActiveByMapDifficultyIdIn(@Param("difficultyIds") List<UUID> difficultyIds);

    @Query(value = """
            SELECT * FROM (
                SELECT * FROM map_difficulty_statistics
                WHERE map_difficulty_id = :difficultyId
                AND created_at > GREATEST(CAST(:since AS timestamptz), NOW() - INTERVAL '24 hours')
                UNION ALL
                SELECT s.* FROM map_difficulty_statistics s
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
                        FROM map_difficulty_statistics
                        WHERE map_difficulty_id = :difficultyId
                        AND created_at > CAST(:since AS timestamptz)
                        AND created_at <= NOW() - INTERVAL '24 hours'
                    ) sub WHERE sub.rn = 1
                ) picked ON s.id = picked.id
            ) combined
            ORDER BY created_at ASC
            """, nativeQuery = true)
    List<MapDifficultyStatistics> findHistoricDownsampled(
            @Param("difficultyId") UUID difficultyId,
            @Param("since") Instant since);
}
