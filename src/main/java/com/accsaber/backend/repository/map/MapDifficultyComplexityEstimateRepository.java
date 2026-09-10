package com.accsaber.backend.repository.map;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.dto.projection.ActiveComplexityRow;
import com.accsaber.backend.model.entity.map.ComplexityEstimateSource;
import com.accsaber.backend.model.entity.map.MapDifficultyComplexityEstimate;

public interface MapDifficultyComplexityEstimateRepository extends JpaRepository<MapDifficultyComplexityEstimate, UUID> {

    Optional<MapDifficultyComplexityEstimate> findByMapDifficultyIdAndSource(UUID mapDifficultyId,
            ComplexityEstimateSource source);

    Optional<MapDifficultyComplexityEstimate> findFirstBySourceOrderByUpdatedAtDesc(ComplexityEstimateSource source);

    @Query("""
            SELECT new com.accsaber.backend.model.dto.projection.ActiveComplexityRow(e.mapDifficulty.id, e.complexity)
            FROM MapDifficultyComplexityEstimate e
            WHERE e.source = :source
            """)
    List<ActiveComplexityRow> findRowsBySource(@Param("source") ComplexityEstimateSource source);

    @Query("""
            SELECT e FROM MapDifficultyComplexityEstimate e
            JOIN FETCH e.mapDifficulty d
            WHERE d.id IN :difficultyIds
            """)
    List<MapDifficultyComplexityEstimate> findAllByDifficultyIds(@Param("difficultyIds") List<UUID> difficultyIds);
}
