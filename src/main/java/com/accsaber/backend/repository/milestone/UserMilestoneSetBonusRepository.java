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

    @Query(value = """
            SELECT COALESCE(SUM(ms.set_bonus_xp), 0)
            FROM user_milestone_set_bonuses umsb
            JOIN milestone_sets ms ON umsb.milestone_set_id = ms.id
            WHERE umsb.user_id = :userId
            AND umsb.claimed_at >= NOW() - INTERVAL '24 hours'
            """, nativeQuery = true)
    java.math.BigDecimal sumSetBonusXpGainedLast24h(@Param("userId") Long userId);
}
