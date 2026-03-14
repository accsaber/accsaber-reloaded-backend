package com.accsaber.backend.repository.milestone;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.milestone.Milestone;

public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {

    List<Milestone> findByActiveTrue();

    Optional<Milestone> findByIdAndActiveTrue(UUID id);

    @Query("""
            SELECT m FROM Milestone m
            LEFT JOIN FETCH m.milestoneSet
            LEFT JOIN FETCH m.category
            WHERE m.id = :id AND m.active = true
            """)
    Optional<Milestone> findByIdAndActiveTrueEager(@Param("id") UUID id);

    List<Milestone> findByMilestoneSet_IdAndActiveTrue(UUID setId);

    List<Milestone> findByCategory_IdAndActiveTrue(UUID categoryId);

    @Query("""
            SELECT m FROM Milestone m
            WHERE m.active = true
            AND (:setId IS NULL OR m.milestoneSet.id = :setId)
            AND (:categoryId IS NULL OR m.category.id = :categoryId)
            AND (:type IS NULL OR m.type = :type)
            """)
    Page<Milestone> findAllActiveFiltered(
            @Param("setId") UUID setId,
            @Param("categoryId") UUID categoryId,
            @Param("type") String type,
            Pageable pageable);

    @Query("""
            SELECT m FROM Milestone m
            LEFT JOIN FETCH m.milestoneSet
            WHERE m.active = true
            AND m.id NOT IN (
                SELECT uml.milestone.id FROM UserMilestoneLink uml
                WHERE uml.user.id = :userId AND uml.completed = true
            )
            """)
    List<Milestone> findActiveUncompletedForUser(@Param("userId") Long userId);

    @Query("""
            SELECT m FROM Milestone m
            LEFT JOIN FETCH m.milestoneSet
            WHERE m.active = true
            AND m.id NOT IN (
                SELECT uml.milestone.id FROM UserMilestoneLink uml
                WHERE uml.user.id = :userId AND uml.completed = true
            )
            AND (
                (m.category IS NULL AND NOT EXISTS (
                    SELECT mdml FROM MapDifficultyMilestoneLink mdml WHERE mdml.milestone = m
                ))
                OR (m.category.id = :categoryId AND NOT EXISTS (
                    SELECT mdml FROM MapDifficultyMilestoneLink mdml WHERE mdml.milestone = m
                ))
                OR EXISTS (
                    SELECT mdml FROM MapDifficultyMilestoneLink mdml
                    WHERE mdml.milestone = m AND mdml.mapDifficulty.id = :mapDifficultyId
                )
            )
            """)
    List<Milestone> findActiveUncompletedForUserScoped(
            @Param("userId") Long userId,
            @Param("categoryId") UUID categoryId,
            @Param("mapDifficultyId") UUID mapDifficultyId);

    @Query("""
            SELECT COUNT(m) FROM Milestone m
            WHERE m.milestoneSet.id = :setId AND m.active = true
            """)
    long countActiveBySetId(@Param("setId") UUID setId);
}
