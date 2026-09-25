package com.accsaber.backend.repository.mission;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.MissionStatus;
import com.accsaber.backend.model.entity.mission.UserMission;

public interface UserMissionRepository extends JpaRepository<UserMission, UUID> {

        @EntityGraph(attributePaths = {
                        "template", "category", "targetMapDifficulty", "targetMapDifficulty.map",
                        "targetPlayer", "itemReward", "itemReward.type"
        })
        List<UserMission> findByUser_IdAndStatus(Long userId, MissionStatus status);

        @EntityGraph(attributePaths = {
                        "template", "category", "targetMapDifficulty", "targetMapDifficulty.map",
                        "targetPlayer", "itemReward", "itemReward.type"
        })
        List<UserMission> findByUser_IdAndPoolAndStatus(Long userId, MissionPool pool, MissionStatus status);

        long countByUser_IdAndPoolAndStatus(Long userId, MissionPool pool, MissionStatus status);

        @Query("""
                        SELECT COUNT(m) FROM UserMission m
                        WHERE m.user.id = :userId
                          AND m.pool = :pool
                          AND m.expiresAt > :now
                          AND m.status IN (com.accsaber.backend.model.entity.mission.MissionStatus.active,
                                           com.accsaber.backend.model.entity.mission.MissionStatus.completed)
                        """)
        long countCurrentCycle(@Param("userId") Long userId, @Param("pool") MissionPool pool,
                        @Param("now") Instant now);

        @Query("""
                        SELECT m FROM UserMission m
                        WHERE m.user.id = :userId
                          AND m.status = com.accsaber.backend.model.entity.mission.MissionStatus.active
                          AND m.targetMapDifficulty.id = :mapDifficultyId
                        """)
        List<UserMission> findActiveByUserAndTargetMap(
                        @Param("userId") Long userId,
                        @Param("mapDifficultyId") UUID mapDifficultyId);

        @Query("""
                        SELECT m FROM UserMission m
                        JOIN FETCH m.template
                        LEFT JOIN FETCH m.category
                        LEFT JOIN FETCH m.targetMapDifficulty d
                        LEFT JOIN FETCH d.map
                        LEFT JOIN FETCH m.targetPlayer
                        LEFT JOIN FETCH m.itemReward ir
                        LEFT JOIN FETCH ir.type
                        WHERE m.user.id = :userId
                          AND m.status = com.accsaber.backend.model.entity.mission.MissionStatus.active
                        """)
        List<UserMission> findAllActiveByUser(@Param("userId") Long userId);

        @Query("""
                        SELECT m FROM UserMission m
                        JOIN FETCH m.template
                        LEFT JOIN FETCH m.category
                        LEFT JOIN FETCH m.targetMapDifficulty d
                        LEFT JOIN FETCH d.map
                        LEFT JOIN FETCH m.targetPlayer
                        LEFT JOIN FETCH m.itemReward ir
                        LEFT JOIN FETCH ir.type
                        WHERE m.user.id = :userId
                          AND (m.status = com.accsaber.backend.model.entity.mission.MissionStatus.active
                               OR (m.status = com.accsaber.backend.model.entity.mission.MissionStatus.completed
                                   AND m.expiresAt > :now))
                        """)
        List<UserMission> findCurrentByUser(@Param("userId") Long userId, @Param("now") Instant now);

        @Query("""
                        SELECT m FROM UserMission m
                        JOIN FETCH m.template
                        LEFT JOIN FETCH m.category
                        LEFT JOIN FETCH m.targetMapDifficulty d
                        LEFT JOIN FETCH d.map
                        LEFT JOIN FETCH m.targetPlayer
                        LEFT JOIN FETCH m.itemReward ir
                        LEFT JOIN FETCH ir.type
                        WHERE m.user.id = :userId
                          AND m.pool = :pool
                          AND (m.status = com.accsaber.backend.model.entity.mission.MissionStatus.active
                               OR (m.status = com.accsaber.backend.model.entity.mission.MissionStatus.completed
                                   AND m.expiresAt > :now))
                        """)
        List<UserMission> findCurrentByUserAndPool(
                        @Param("userId") Long userId,
                        @Param("pool") MissionPool pool,
                        @Param("now") Instant now);

        @Modifying
        @Query("""
                        UPDATE UserMission m
                        SET m.status = com.accsaber.backend.model.entity.mission.MissionStatus.voided
                        WHERE m.user.id = :userId
                          AND m.status = com.accsaber.backend.model.entity.mission.MissionStatus.active
                          AND m.pool <> com.accsaber.backend.model.entity.mission.MissionPool.event
                        """)
        int voidActiveForUser(@Param("userId") Long userId);

        @Modifying
        @Query("""
                        UPDATE UserMission m
                        SET m.status = com.accsaber.backend.model.entity.mission.MissionStatus.voided
                        WHERE m.user.id = :userId
                          AND m.pool = :pool
                          AND m.status = com.accsaber.backend.model.entity.mission.MissionStatus.active
                        """)
        int voidActiveForUserAndPool(@Param("userId") Long userId, @Param("pool") MissionPool pool);

        @Modifying
        @Query("""
                        UPDATE UserMission m
                        SET m.status = com.accsaber.backend.model.entity.mission.MissionStatus.expired
                        WHERE m.pool = :pool
                          AND m.status = com.accsaber.backend.model.entity.mission.MissionStatus.active
                        """)
        int expireByPool(@Param("pool") MissionPool pool);

        @Modifying
        @Query("""
                        UPDATE UserMission m
                        SET m.status = com.accsaber.backend.model.entity.mission.MissionStatus.expired
                        WHERE m.pool IN (com.accsaber.backend.model.entity.mission.MissionPool.event,
                                         com.accsaber.backend.model.entity.mission.MissionPool.community)
                          AND m.status = com.accsaber.backend.model.entity.mission.MissionStatus.active
                          AND m.expiresAt < :now
                        """)
        int expireOutOfWindowMissions(@Param("now") Instant now);

        @Query("""
                        SELECT m FROM UserMission m
                        JOIN FETCH m.template t
                        LEFT JOIN FETCH t.event
                        LEFT JOIN FETCH m.category
                        LEFT JOIN FETCH m.targetMapDifficulty d
                        LEFT JOIN FETCH d.map
                        LEFT JOIN FETCH m.targetPlayer
                        LEFT JOIN FETCH m.itemReward ir
                        LEFT JOIN FETCH ir.type
                        WHERE m.pool = com.accsaber.backend.model.entity.mission.MissionPool.community
                          AND m.status = com.accsaber.backend.model.entity.mission.MissionStatus.active
                        """)
        List<UserMission> findActiveCommunity();

        @Query("""
                        SELECT m FROM UserMission m
                        JOIN FETCH m.template t
                        LEFT JOIN FETCH t.event
                        LEFT JOIN FETCH m.category
                        LEFT JOIN FETCH m.targetMapDifficulty d
                        LEFT JOIN FETCH d.map
                        LEFT JOIN FETCH m.targetPlayer
                        LEFT JOIN FETCH m.itemReward ir
                        LEFT JOIN FETCH ir.type
                        WHERE m.pool = com.accsaber.backend.model.entity.mission.MissionPool.community
                          AND (:eventId IS NULL OR t.event.id = :eventId)
                          AND (:activeOnly = false
                               OR m.status = com.accsaber.backend.model.entity.mission.MissionStatus.active)
                        ORDER BY m.assignedAt ASC
                        """)
        List<UserMission> findCommunity(@Param("eventId") UUID eventId,
                        @Param("activeOnly") boolean activeOnly);

        @Query("""
                        SELECT m FROM UserMission m
                        JOIN FETCH m.template t
                        LEFT JOIN FETCH t.event
                        LEFT JOIN FETCH m.itemReward ir
                        LEFT JOIN FETCH ir.type
                        WHERE m.id = :id
                          AND m.pool = com.accsaber.backend.model.entity.mission.MissionPool.community
                        """)
        Optional<UserMission> findCommunityById(@Param("id") UUID id);

        @Query("""
                        SELECT m.template.id FROM UserMission m
                        WHERE m.pool = com.accsaber.backend.model.entity.mission.MissionPool.community
                          AND m.status = com.accsaber.backend.model.entity.mission.MissionStatus.active
                        """)
        List<UUID> findTemplateIdsWithActiveCommunityMission();

        @Modifying
        @Query(value = """
                        UPDATE user_missions
                        SET progress_count = progress_count + :count,
                            progress_ap    = progress_ap + :ap
                        WHERE id = :id
                          AND user_id IS NULL
                          AND status = 'active'
                        """, nativeQuery = true)
        int bankCommunityProgress(@Param("id") UUID id, @Param("count") int count, @Param("ap") double ap);

        @Modifying
        @Query(value = """
                        UPDATE user_missions
                        SET status = 'completed', completed_at = :now
                        WHERE id = :id
                          AND user_id IS NULL
                          AND status = 'active'
                          AND ((target_count IS NOT NULL AND progress_count >= target_count)
                            OR (target_xp    IS NOT NULL AND progress_count >= target_xp)
                            OR (target_ap    IS NOT NULL AND progress_ap    >= target_ap))
                        """, nativeQuery = true)
        int claimCommunityCompletion(@Param("id") UUID id, @Param("now") Instant now);

        @Query(value = """
                        SELECT COALESCE(SUM(um.xp_reward), 0)
                        FROM user_missions um
                        JOIN mission_templates mt ON mt.id = um.template_id
                        WHERE um.user_id = :userId
                          AND um.status = 'completed'
                          AND um.completed_at >= NOW() - INTERVAL '24 hours'
                          AND mt.event_id IS NULL
                        """, nativeQuery = true)
        double sumMissionXpGainedLast24h(@Param("userId") Long userId);

        @Query(value = """
                        SELECT COALESCE(SUM(e.xp), 0) FROM (
                            SELECT um.xp_reward AS xp
                            FROM user_missions um
                            JOIN mission_templates mt ON mt.id = um.template_id
                            WHERE um.user_id = :userId
                              AND um.status = 'completed'
                              AND um.completed_at >= NOW() - INTERVAL '24 hours'
                              AND mt.event_id IS NOT NULL
                            UNION ALL
                            SELECT uep.bonus_xp
                            FROM user_event_profiles uep
                            WHERE uep.user_id = :userId
                              AND uep.bonus_awarded_at >= NOW() - INTERVAL '24 hours'
                        ) e
                        """, nativeQuery = true)
        double sumEventXpGainedLast24h(@Param("userId") Long userId);

        long countByUser_IdAndTemplate_IdAndStatus(Long userId, UUID templateId, MissionStatus status);

        long countByTemplate_IdAndUserIsNullAndStatus(UUID templateId, MissionStatus status);

        interface TemplateStatusView {
                UUID getTemplateId();

                MissionStatus getStatus();
        }

        @Query("""
                        SELECT m.template.id AS templateId, m.status AS status
                        FROM UserMission m
                        WHERE m.user.id = :userId
                          AND m.template.event.id = :eventId
                        """)
        List<TemplateStatusView> findTemplateStatusesByUserAndEvent(
                        @Param("userId") Long userId,
                        @Param("eventId") UUID eventId);

        @Query("""
                        SELECT m FROM UserMission m
                        JOIN FETCH m.template t
                        LEFT JOIN FETCH m.category
                        LEFT JOIN FETCH m.targetMapDifficulty d
                        LEFT JOIN FETCH d.map
                        LEFT JOIN FETCH m.targetPlayer
                        LEFT JOIN FETCH m.itemReward ir
                        LEFT JOIN FETCH ir.type
                        WHERE m.user.id = :userId
                          AND t.event.id = :eventId
                        ORDER BY m.assignedAt ASC
                        """)
        List<UserMission> findByUserAndEvent(@Param("userId") Long userId, @Param("eventId") UUID eventId);

        @Query("""
                        SELECT DISTINCT m.template.id FROM UserMission m
                        WHERE m.user.id = :userId
                          AND m.template.event.id = :eventId
                          AND m.template.active = true
                          AND m.status = com.accsaber.backend.model.entity.mission.MissionStatus.completed
                        """)
        List<UUID> findCompletedTemplateIdsForEvent(@Param("userId") Long userId, @Param("eventId") UUID eventId);
}
