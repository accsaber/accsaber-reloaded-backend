package com.accsaber.backend.repository.milestone;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.milestone.UserMilestoneLink;

public interface UserMilestoneLinkRepository extends JpaRepository<UserMilestoneLink, UUID> {

        Optional<UserMilestoneLink> findByUser_IdAndMilestone_Id(Long userId, UUID milestoneId);

        List<UserMilestoneLink> findByUser_Id(Long userId);

        List<UserMilestoneLink> findByUser_IdAndCompletedTrue(Long userId);

        @Query("""
                        SELECT COUNT(uml) FROM UserMilestoneLink uml
                        WHERE uml.user.id = :userId
                        AND uml.completed = true
                        AND uml.milestone.milestoneSet.id = :setId
                        """)
        long countCompletedByUserAndSet(@Param("userId") Long userId, @Param("setId") UUID setId);

        @Query("""
                        SELECT COALESCE(SUM(uml.milestone.xp), 0) FROM UserMilestoneLink uml
                        WHERE uml.user.id = :userId AND uml.completed = true
                        """)
        java.math.BigDecimal sumCompletedMilestoneXpByUserId(@Param("userId") Long userId);
}
