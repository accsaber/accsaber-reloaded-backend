package com.accsaber.backend.repository.milestone;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.milestone.UserMilestoneLink;

public interface UserMilestoneLinkRepository extends JpaRepository<UserMilestoneLink, UUID> {

        Optional<UserMilestoneLink> findByUser_IdAndMilestone_Id(Long userId, UUID milestoneId);

        List<UserMilestoneLink> findByUser_Id(Long userId);

        List<UserMilestoneLink> findByUser_IdAndMilestone_IdIn(Long userId, List<UUID> milestoneIds);

        List<UserMilestoneLink> findByUser_IdAndCompletedTrue(Long userId);

        @Query("""
                        SELECT uml FROM UserMilestoneLink uml
                        JOIN FETCH uml.milestone m
                        JOIN FETCH m.milestoneSet
                        LEFT JOIN FETCH m.category
                        WHERE uml.user.id = :userId AND uml.completed = true AND m.active = true AND m.status = 'ACTIVE'
                        ORDER BY uml.completedAt DESC
                        """)
        List<UserMilestoneLink> findCompletedByUserWithMilestoneDetails(@Param("userId") Long userId);

        @Query("""
                        SELECT uml FROM UserMilestoneLink uml
                        LEFT JOIN FETCH uml.achievedWithScore s
                        LEFT JOIN FETCH s.mapDifficulty md
                        LEFT JOIN FETCH md.map
                        WHERE uml.user.id = :userId
                        AND uml.milestone.id IN :milestoneIds
                        """)
        List<UserMilestoneLink> findByUserWithScoreDetails(
                        @Param("userId") Long userId,
                        @Param("milestoneIds") List<UUID> milestoneIds);

        @Query("""
                        SELECT uml.milestone.milestoneSet.id, COUNT(uml)
                        FROM UserMilestoneLink uml
                        WHERE uml.user.id = :userId AND uml.completed = true
                        AND uml.milestone.active = true AND uml.milestone.status = 'ACTIVE'
                        GROUP BY uml.milestone.milestoneSet.id
                        """)
        List<Object[]> countCompletedByUserGroupedBySet(@Param("userId") Long userId);

        @Query("""
                        SELECT COUNT(uml) FROM UserMilestoneLink uml
                        WHERE uml.user.id = :userId
                        AND uml.completed = true
                        AND uml.milestone.milestoneSet.id = :setId
                        """)
        long countCompletedByUserAndSet(@Param("userId") Long userId, @Param("setId") UUID setId);

        @Query(value = """
                        SELECT COALESCE(SUM(m.xp), 0)
                        FROM user_milestone_links uml
                        JOIN milestones m ON uml.milestone_id = m.id
                        WHERE uml.user_id = :userId AND uml.completed = true
                        AND (uml.created_at >= NOW() - INTERVAL '24 hours'
                        OR uml.completed_at >= NOW() - INTERVAL '24 hours')
                        """, nativeQuery = true)
        BigDecimal sumMilestoneXpGainedLast24h(@Param("userId") Long userId);

        @Query("""
                        SELECT COALESCE(SUM(uml.milestone.xp), 0) FROM UserMilestoneLink uml
                        WHERE uml.user.id = :userId AND uml.completed = true
                        """)
        java.math.BigDecimal sumCompletedMilestoneXpByUserId(@Param("userId") Long userId);
        @EntityGraph(attributePaths = {"user"})
        @Query(value = """
                        SELECT uml FROM UserMilestoneLink uml
                        JOIN uml.user u
                        WHERE uml.milestone.id = :milestoneId
                        AND uml.completed = true
                        AND u.active = true AND u.banned = false
                        ORDER BY uml.completedAt DESC
                        """,
                        countQuery = """
                        SELECT COUNT(uml) FROM UserMilestoneLink uml
                        JOIN uml.user u
                        WHERE uml.milestone.id = :milestoneId
                        AND uml.completed = true
                        AND u.active = true AND u.banned = false
                        """)
        Page<UserMilestoneLink> findCompletedUsersByMilestoneId(
                        @Param("milestoneId") UUID milestoneId,
                        Pageable pageable);
}
