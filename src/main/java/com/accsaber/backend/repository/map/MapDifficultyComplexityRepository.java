package com.accsaber.backend.repository.map;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.map.MapDifficultyComplexity;

import jakarta.persistence.LockModeType;

public interface MapDifficultyComplexityRepository extends JpaRepository<MapDifficultyComplexity, UUID> {

        Optional<MapDifficultyComplexity> findByMapDifficultyIdAndActiveTrue(UUID mapDifficultyId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT c FROM MapDifficultyComplexity c WHERE c.mapDifficulty.id = :id AND c.active = true")
        Optional<MapDifficultyComplexity> findActiveForUpdate(@Param("id") UUID mapDifficultyId);

        List<MapDifficultyComplexity> findByMapDifficultyIdOrderByCreatedAtDesc(UUID mapDifficultyId);

        @Query("""
                        SELECT c FROM MapDifficultyComplexity c
                        WHERE c.mapDifficulty.id IN :difficultyIds AND c.active = true
                        """)
        List<MapDifficultyComplexity> findActiveByMapDifficultyIdIn(@Param("difficultyIds") List<UUID> difficultyIds);

        @Query("""
                        SELECT c FROM MapDifficultyComplexity c
                        JOIN c.mapDifficulty d
                        WHERE d.map.id = :mapId
                        ORDER BY c.createdAt DESC
                        """)
        List<MapDifficultyComplexity> findAllByMapIdOrderByCreatedAtDesc(@Param("mapId") UUID mapId);
}
