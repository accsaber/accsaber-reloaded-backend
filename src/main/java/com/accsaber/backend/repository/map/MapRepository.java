package com.accsaber.backend.repository.map;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;

public interface MapRepository extends JpaRepository<Map, UUID> {

        Optional<Map> findByIdAndActiveTrue(UUID id);

        Optional<Map> findBySongHashAndActiveTrue(String songHash);

        java.util.List<Map> findByActiveTrue();

        @Query("""
                        SELECT m FROM Map m
                        WHERE m.active = true
                        AND m.beatsaverCode = :beatsaverCode
                        ORDER BY m.createdAt DESC
                        """)
        java.util.List<Map> findActiveByBeatsaverCodeLatestFirst(
                        @Param("beatsaverCode") String beatsaverCode,
                        Pageable pageable);

        @Query("""
                        SELECT m FROM Map m
                        WHERE m.active = true
                        AND m.beatsaverCode = :beatsaverCode
                        AND EXISTS (
                            SELECT 1 FROM MapDifficulty d
                            WHERE d.map = m AND d.active = true
                            AND (:difficulty IS NULL OR d.difficulty = :difficulty)
                            AND (CAST(:characteristic AS string) IS NULL
                                 OR LOWER(d.characteristic) = LOWER(CAST(:characteristic AS string)))
                        )
                        ORDER BY m.createdAt DESC
                        """)
        java.util.List<Map> findActiveByBeatsaverCodeMatchingDifficulty(
                        @Param("beatsaverCode") String beatsaverCode,
                        @Param("difficulty") Difficulty difficulty,
                        @Param("characteristic") String characteristic,
                        Pageable pageable);

        @Query(value = """
                        SELECT m FROM Map m
                        WHERE m.active = true
                        AND (:warBanned IS NULL OR m.warBanned = :warBanned)
                        AND m.id IN (
                                SELECT DISTINCT d.map.id FROM MapDifficulty d
                                WHERE d.active = true
                                AND (:categoryId IS NULL OR d.category.id = :categoryId)
                                AND ((:status IS NULL AND d.status <> com.accsaber.backend.model.entity.map.MapDifficultyStatus.CAMPAIGN)
                                     OR d.status = :status)
                        )
                        """, countQuery = """
                        SELECT COUNT(DISTINCT d.map.id) FROM MapDifficulty d
                        JOIN d.map m
                        WHERE d.active = true
                        AND (:warBanned IS NULL OR m.warBanned = :warBanned)
                        AND (:categoryId IS NULL OR d.category.id = :categoryId)
                        AND ((:status IS NULL AND d.status <> com.accsaber.backend.model.entity.map.MapDifficultyStatus.CAMPAIGN)
                             OR d.status = :status)
                        """)
        Page<Map> findByDifficultyFilters(
                        @Param("categoryId") UUID categoryId,
                        @Param("status") MapDifficultyStatus status,
                        @Param("warBanned") Boolean warBanned,
                        Pageable pageable);

        @Query(value = """
                        SELECT m FROM Map m
                        WHERE m.active = true
                        AND (:warBanned IS NULL OR m.warBanned = :warBanned)
                        AND (search_normalize(m.songName) LIKE CONCAT('%', search_normalize(CAST(:search AS string)), '%')
                             OR search_normalize(m.songSubName) LIKE CONCAT('%', search_normalize(CAST(:search AS string)), '%')
                             OR search_normalize(m.songAuthor) LIKE CONCAT('%', search_normalize(CAST(:search AS string)), '%')
                             OR search_normalize(m.mapAuthor) LIKE CONCAT('%', search_normalize(CAST(:search AS string)), '%'))
                        AND m.id IN (
                                SELECT DISTINCT d.map.id FROM MapDifficulty d
                                WHERE d.active = true
                                AND (:categoryId IS NULL OR d.category.id = :categoryId)
                                AND ((:status IS NULL AND d.status <> com.accsaber.backend.model.entity.map.MapDifficultyStatus.CAMPAIGN)
                                     OR d.status = :status)
                        )
                        """, countQuery = """
                        SELECT COUNT(DISTINCT d.map.id) FROM MapDifficulty d
                        JOIN d.map m
                        WHERE d.active = true
                        AND m.active = true
                        AND (:warBanned IS NULL OR m.warBanned = :warBanned)
                        AND (:categoryId IS NULL OR d.category.id = :categoryId)
                        AND ((:status IS NULL AND d.status <> com.accsaber.backend.model.entity.map.MapDifficultyStatus.CAMPAIGN)
                             OR d.status = :status)
                        AND (search_normalize(m.songName) LIKE CONCAT('%', search_normalize(CAST(:search AS string)), '%')
                             OR search_normalize(m.songSubName) LIKE CONCAT('%', search_normalize(CAST(:search AS string)), '%')
                             OR search_normalize(m.songAuthor) LIKE CONCAT('%', search_normalize(CAST(:search AS string)), '%')
                             OR search_normalize(m.mapAuthor) LIKE CONCAT('%', search_normalize(CAST(:search AS string)), '%'))
                        """)
        Page<Map> findByDifficultyFiltersWithSearch(
                        @Param("categoryId") UUID categoryId,
                        @Param("status") MapDifficultyStatus status,
                        @Param("search") String search,
                        @Param("warBanned") Boolean warBanned,
                        Pageable pageable);
}
