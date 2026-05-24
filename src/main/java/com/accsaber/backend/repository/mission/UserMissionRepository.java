package com.accsaber.backend.repository.mission;

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
                        "targetPlayer", "itemReward"
        })
        List<UserMission> findByUser_IdAndStatus(Long userId, MissionStatus status);

        @EntityGraph(attributePaths = {
                        "template", "category", "targetMapDifficulty", "targetMapDifficulty.map",
                        "targetPlayer", "itemReward"
        })
        List<UserMission> findByUser_IdAndPoolAndStatus(Long userId, MissionPool pool, MissionStatus status);

        long countByUser_IdAndPoolAndStatus(Long userId, MissionPool pool, MissionStatus status);

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
                        LEFT JOIN FETCH m.itemReward
                        WHERE m.user.id = :userId
                          AND m.status = com.accsaber.backend.model.entity.mission.MissionStatus.active
                        """)
        List<UserMission> findAllActiveByUser(@Param("userId") Long userId);

        @Modifying
        @Query("""
                        DELETE FROM UserMission m
                        WHERE m.user.id = :userId
                          AND m.status = com.accsaber.backend.model.entity.mission.MissionStatus.active
                        """)
        int deleteActiveForUser(@Param("userId") Long userId);

        @Modifying
        @Query("""
                        DELETE FROM UserMission m
                        WHERE m.status = com.accsaber.backend.model.entity.mission.MissionStatus.active
                          AND m.targetMapDifficulty.id = :mapDifficultyId
                        """)
        int deleteActiveByMapDifficulty(@Param("mapDifficultyId") UUID mapDifficultyId);

        @Modifying
        @Query("""
                        DELETE FROM UserMission m
                        WHERE m.status = com.accsaber.backend.model.entity.mission.MissionStatus.active
                          AND m.targetPlayer.id = :playerId
                        """)
        int deleteActiveByTargetPlayer(@Param("playerId") Long playerId);

        @Modifying
        @Query("""
                        DELETE FROM UserMission m
                        WHERE m.pool = :pool
                          AND m.status <> com.accsaber.backend.model.entity.mission.MissionStatus.completed
                        """)
        int deleteNonCompletedByPool(@Param("pool") MissionPool pool);
}
