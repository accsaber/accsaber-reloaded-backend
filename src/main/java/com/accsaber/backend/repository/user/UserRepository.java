package com.accsaber.backend.repository.user;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.entity.user.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByIdAndActiveTrue(Long id);

    List<User> findByActiveTrue();

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.totalXp = u.totalXp + :xp WHERE u.id = :id")
    void addXp(@Param("id") Long id, @Param("xp") BigDecimal xp);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE users u SET total_xp =
                COALESCE(sx.score_xp, 0)
                + COALESCE(mx.milestone_xp, 0)
                + COALESCE(bx.bonus_xp, 0)
                + COALESCE(cx.campaign_map_xp, 0)
                + COALESCE(cmx.campaign_milestone_xp, 0),
            updated_at = NOW()
            FROM (
                SELECT user_id, SUM(xp_gained) AS score_xp FROM scores GROUP BY user_id
            ) sx
            FULL OUTER JOIN (
                SELECT uml.user_id, SUM(m.xp) AS milestone_xp
                FROM user_milestone_links uml JOIN milestones m ON uml.milestone_id = m.id
                WHERE uml.completed = true
                GROUP BY uml.user_id
            ) mx ON mx.user_id = sx.user_id
            FULL OUTER JOIN (
                SELECT umsb.user_id, SUM(ms.set_bonus_xp) AS bonus_xp
                FROM user_milestone_set_bonuses umsb JOIN milestone_sets ms ON umsb.milestone_set_id = ms.id
                GROUP BY umsb.user_id
            ) bx ON bx.user_id = COALESCE(sx.user_id, mx.user_id)
            FULL OUTER JOIN (
                SELECT ucs.user_id, SUM(cm.xp) AS campaign_map_xp
                FROM user_campaign_scores ucs
                JOIN campaign_maps cm ON ucs.campaign_map_id = cm.id
                JOIN campaigns c ON ucs.campaign_id = c.id
                WHERE ucs.active = true AND c.verified = true AND cm.active = true
                GROUP BY ucs.user_id
            ) cx ON cx.user_id = COALESCE(sx.user_id, mx.user_id, bx.user_id)
            FULL OUTER JOIN (
                SELECT ucs.user_id, SUM(cmil.xp) AS campaign_milestone_xp
                FROM user_campaign_scores ucs
                JOIN campaign_maps cm ON ucs.campaign_map_id = cm.id
                JOIN campaign_milestones cmil ON cm.milestone_for_id = cmil.id
                JOIN campaigns c ON ucs.campaign_id = c.id
                WHERE ucs.active = true AND c.verified = true AND cm.active = true AND cmil.active = true
                GROUP BY ucs.user_id
            ) cmx ON cmx.user_id = COALESCE(sx.user_id, mx.user_id, bx.user_id, cx.user_id)
            WHERE u.id = COALESCE(sx.user_id, mx.user_id, bx.user_id, cx.user_id, cmx.user_id)
            AND u.active = true
            """, nativeQuery = true)
    void recalculateTotalXpForAllActiveUsers();
}
