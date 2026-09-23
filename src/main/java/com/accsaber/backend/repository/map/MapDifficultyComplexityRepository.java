package com.accsaber.backend.repository.map;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.dto.projection.ActiveComplexityRow;
import com.accsaber.backend.model.dto.projection.ReweightRoundMapRow;
import com.accsaber.backend.model.entity.map.MapDifficultyComplexity;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;

import jakarta.persistence.LockModeType;

public interface MapDifficultyComplexityRepository extends JpaRepository<MapDifficultyComplexity, UUID> {

        Optional<MapDifficultyComplexity> findByMapDifficultyIdAndActiveTrue(UUID mapDifficultyId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT c FROM MapDifficultyComplexity c WHERE c.mapDifficulty.id = :id AND c.active = true")
        Optional<MapDifficultyComplexity> findActiveForUpdate(@Param("id") UUID mapDifficultyId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
                        SELECT c FROM MapDifficultyComplexity c
                        WHERE c.mapDifficulty.id IN :ids AND c.active = true
                        ORDER BY c.id
                        """)
        List<MapDifficultyComplexity> findActiveForUpdateIn(@Param("ids") List<UUID> mapDifficultyIds);

        List<MapDifficultyComplexity> findByMapDifficultyIdOrderByCreatedAtDesc(UUID mapDifficultyId);

        @Query("""
                        SELECT c FROM MapDifficultyComplexity c
                        WHERE c.mapDifficulty.id IN :difficultyIds AND c.active = true
                        """)
        List<MapDifficultyComplexity> findActiveByMapDifficultyIdIn(@Param("difficultyIds") List<UUID> difficultyIds);

        @Query("""
                        SELECT new com.accsaber.backend.model.dto.projection.ActiveComplexityRow(d.id, c.complexity)
                        FROM MapDifficultyComplexity c
                        JOIN c.mapDifficulty d
                        WHERE c.active = true AND d.active = true AND d.status = :status
                        """)
        List<ActiveComplexityRow> findActiveRowsByDifficultyStatus(@Param("status") MapDifficultyStatus status);

        @Query("""
                        SELECT c FROM MapDifficultyComplexity c
                        JOIN c.mapDifficulty d
                        WHERE d.map.id = :mapId
                        ORDER BY c.createdAt DESC
                        """)
        List<MapDifficultyComplexity> findAllByMapIdOrderByCreatedAtDesc(@Param("mapId") UUID mapId);

        @Query("""
                        SELECT new com.accsaber.backend.model.dto.projection.ReweightRoundMapRow(
                                c.round.id, m.id, d.id, m.songName, d.difficulty, p.complexity, c.complexity)
                        FROM MapDifficultyComplexity c
                        JOIN c.mapDifficulty d
                        JOIN d.map m
                        LEFT JOIN c.supersedes p
                        WHERE c.round.id IN :roundIds
                        ORDER BY c.createdAt, c.id
                        """)
        List<ReweightRoundMapRow> findMapRowsByRoundIds(@Param("roundIds") List<UUID> roundIds);
}
