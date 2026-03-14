package com.accsaber.backend.repository.map;

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
}
