package com.accsaber.backend.repository.map;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;

public interface MapRepository extends JpaRepository<Map, UUID> {

        Optional<Map> findByIdAndActiveTrue(UUID id);

        Optional<Map> findBySongHashAndActiveTrue(String songHash);

        Optional<Map> findByBeatsaverCodeAndActiveTrue(String beatsaverCode);

        @Query(value = """
                        SELECT m FROM Map m
                        WHERE m.active = true
                        AND m.id IN (
                                SELECT DISTINCT d.map.id FROM MapDifficulty d
                                WHERE d.active = true
                                AND (:categoryId IS NULL OR d.category.id = :categoryId)
                                AND (:status IS NULL OR d.status = :status)
                        )
                        """, countQuery = """
                        SELECT COUNT(DISTINCT d.map.id) FROM MapDifficulty d
                        WHERE d.active = true
                        AND (:categoryId IS NULL OR d.category.id = :categoryId)
                        AND (:status IS NULL OR d.status = :status)
                        """)
        Page<Map> findByDifficultyFilters(
                        @Param("categoryId") UUID categoryId,
                        @Param("status") MapDifficultyStatus status,
                        Pageable pageable);

        @Query(value = """
                        SELECT m FROM Map m
                        WHERE m.active = true
                        AND (LOWER(m.songName) LIKE LOWER(CONCAT('%', :search, '%'))
                             OR LOWER(m.songAuthor) LIKE LOWER(CONCAT('%', :search, '%'))
                             OR LOWER(m.mapAuthor) LIKE LOWER(CONCAT('%', :search, '%')))
                        AND m.id IN (
                                SELECT DISTINCT d.map.id FROM MapDifficulty d
                                WHERE d.active = true
                                AND (:categoryId IS NULL OR d.category.id = :categoryId)
                                AND (:status IS NULL OR d.status = :status)
                        )
                        """, countQuery = """
                        SELECT COUNT(DISTINCT d.map.id) FROM MapDifficulty d
                        JOIN d.map m
                        WHERE d.active = true
                        AND m.active = true
                        AND (:categoryId IS NULL OR d.category.id = :categoryId)
                        AND (:status IS NULL OR d.status = :status)
                        AND (LOWER(m.songName) LIKE LOWER(CONCAT('%', :search, '%'))
                             OR LOWER(m.songAuthor) LIKE LOWER(CONCAT('%', :search, '%'))
                             OR LOWER(m.mapAuthor) LIKE LOWER(CONCAT('%', :search, '%')))
                        """)
        Page<Map> findByDifficultyFiltersWithSearch(
                        @Param("categoryId") UUID categoryId,
                        @Param("status") MapDifficultyStatus status,
                        @Param("search") String search,
                        Pageable pageable);
}
