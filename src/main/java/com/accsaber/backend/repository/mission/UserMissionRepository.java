package com.accsaber.backend.repository.mission;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

        long countByUser_IdAndPoolAndExpiresAtAfter(Long userId, MissionPool pool, Instant now);

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
                        DELETE FROM UserMission m
                        WHERE m.user.id = :userId
                          AND m.status = com.accsaber.backend.model.entity.mission.MissionStatus.active
                          AND m.pool <> com.accsaber.backend.model.entity.mission.MissionPool.event
                        """)
        int deleteActiveForUser(@Param("userId") Long userId);

        @Modifying
        @Query("""
                        DELETE FROM UserMission m
                        WHERE m.user.id = :userId
                          AND m.pool = :pool
                          AND m.status = com.accsaber.backend.model.entity.mission.MissionStatus.active
                        """)
        int deleteActiveForUserAndPool(@Param("userId") Long userId, @Param("pool") MissionPool pool);

        @Modifying
        @Query("""
                        DELETE FROM UserMission m
                        WHERE m.pool = :pool
                          AND m.status <> com.accsaber.backend.model.entity.mission.MissionStatus.completed
                        """)
        int deleteNonCompletedByPool(@Param("pool") MissionPool pool);

        @Modifying
        @Query("""
                        UPDATE UserMission m
                        SET m.status = com.accsaber.backend.model.entity.mission.MissionStatus.expired
                        WHERE m.pool = com.accsaber.backend.model.entity.mission.MissionPool.event
                          AND m.status = com.accsaber.backend.model.entity.mission.MissionStatus.active
                          AND m.expiresAt < :now
                        """)
        int expireEventMissions(@Param("now") Instant now);

        @Query(value = """
                        SELECT COALESCE(SUM(xp_reward), 0)
                        FROM user_missions
                        WHERE user_id = :userId
                          AND status = 'completed'
                          AND completed_at >= NOW() - INTERVAL '24 hours'
                        """, nativeQuery = true)
        BigDecimal sumMissionXpGainedLast24h(@Param("userId") Long userId);

        long countByUser_IdAndTemplate_IdAndStatus(Long userId, UUID templateId, MissionStatus status);

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
