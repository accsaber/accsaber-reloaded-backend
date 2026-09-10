package com.accsaber.backend.repository.map;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.dto.projection.ActiveComplexityRow;
import com.accsaber.backend.model.entity.map.MapDifficultyComplexityEstimate;

public interface MapDifficultyComplexityEstimateRepository extends JpaRepository<MapDifficultyComplexityEstimate, UUID> {

    Optional<MapDifficultyComplexityEstimate> findByMapDifficultyId(UUID mapDifficultyId);

    Optional<MapDifficultyComplexityEstimate> findFirstByOrderByUpdatedAtDesc();

    @Query("""
            SELECT e FROM MapDifficultyComplexityEstimate e
            JOIN FETCH e.mapDifficulty d
            JOIN FETCH d.category
            WHERE d.active = true
            """)
    List<MapDifficultyComplexityEstimate> findAllWithCategory();

    @Query("""
            SELECT new com.accsaber.backend.model.dto.projection.ActiveComplexityRow(e.mapDifficulty.id, e.complexity)
            FROM MapDifficultyComplexityEstimate e
            """)
    List<ActiveComplexityRow> findRows();

    @Query("""
            SELECT e FROM MapDifficultyComplexityEstimate e
            JOIN FETCH e.mapDifficulty d
            WHERE d.id IN :difficultyIds
            """)
    List<MapDifficultyComplexityEstimate> findAllByDifficultyIds(@Param("difficultyIds") List<UUID> difficultyIds);
}
