package com.accsaber.backend.repository.milestone;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.milestone.UserMilestoneSetBonus;

public interface UserMilestoneSetBonusRepository extends JpaRepository<UserMilestoneSetBonus, UUID> {

    boolean existsByUser_IdAndMilestoneSet_Id(Long userId, UUID milestoneSetId);

    @Query("""
            SELECT COALESCE(SUM(umsb.milestoneSet.setBonusXp), 0) FROM UserMilestoneSetBonus umsb
            WHERE umsb.user.id = :userId
            """)
    java.math.BigDecimal sumSetBonusXpByUserId(@Param("userId") Long userId);
}
