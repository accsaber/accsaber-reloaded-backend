package com.accsaber.backend.repository.mission;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.mission.MissionContribution;

public interface MissionContributionRepository
        extends JpaRepository<MissionContribution, MissionContribution.Key> {

    @Query(value = """
            WITH prior AS (
                SELECT contribution
                FROM mission_contributions
                WHERE user_mission_id = :missionId AND user_id = :userId
            ),
            banked AS (
                INSERT INTO mission_contributions AS c
                    (user_mission_id, user_id, contribution, first_at, last_at)
                VALUES (:missionId, :userId,
                        LEAST(CAST(:amount AS double precision),
                              COALESCE(CAST(:cap AS double precision), CAST(:amount AS double precision))),
                        :now, :now)
                ON CONFLICT (user_mission_id, user_id) DO UPDATE
                    SET contribution = LEAST(c.contribution + CAST(:amount AS double precision),
                                             COALESCE(CAST(:cap AS double precision),
                                                      c.contribution + CAST(:amount AS double precision))),
                        last_at = :now
                RETURNING contribution
            )
            SELECT banked.contribution - COALESCE((SELECT contribution FROM prior), 0)
            FROM banked
            """, nativeQuery = true)
    double acceptContribution(
            @Param("missionId") UUID missionId,
            @Param("userId") Long userId,
            @Param("amount") double amount,
            @Param("cap") Double cap,
            @Param("now") Instant now);

    interface ContributionView {
        UUID getMissionId();

        double getContribution();
    }

    @Query("""
            SELECT c.mission.id AS missionId, c.contribution AS contribution
            FROM MissionContribution c
            WHERE c.user.id = :userId AND c.mission.id IN :missionIds
            """)
    List<ContributionView> findContributionsByUser(
            @Param("userId") Long userId,
            @Param("missionIds") List<UUID> missionIds);

    interface ContributorCountView {
        UUID getMissionId();

        long getContributors();
    }

    @Query("""
            SELECT c.mission.id AS missionId, COUNT(c) AS contributors
            FROM MissionContribution c
            WHERE c.mission.id IN :missionIds
            GROUP BY c.mission.id
            """)
    List<ContributorCountView> countContributors(@Param("missionIds") List<UUID> missionIds);

    @Query(value = """
            SELECT c FROM MissionContribution c
            JOIN FETCH c.user
            WHERE c.mission.id = :missionId
            ORDER BY c.contribution DESC, c.firstAt ASC
            """,
            countQuery = """
            SELECT COUNT(c) FROM MissionContribution c
            WHERE c.mission.id = :missionId
            """)
    Page<MissionContribution> findLeaderboard(@Param("missionId") UUID missionId, Pageable pageable);

    @Query("""
            SELECT c FROM MissionContribution c
            JOIN FETCH c.user
            WHERE c.mission.id = :missionId AND c.rewardedAt IS NULL
            """)
    List<MissionContribution> findUnrewarded(@Param("missionId") UUID missionId, Pageable pageable);

    @Modifying
    @Query("""
            UPDATE MissionContribution c
            SET c.rewardedAt = :now
            WHERE c.mission.id = :missionId AND c.user.id = :userId AND c.rewardedAt IS NULL
            """)
    int markRewarded(@Param("missionId") UUID missionId, @Param("userId") Long userId,
            @Param("now") Instant now);

    @Query("""
            SELECT DISTINCT c.mission.id FROM MissionContribution c
            WHERE c.rewardedAt IS NULL
              AND c.mission.status = com.accsaber.backend.model.entity.mission.MissionStatus.completed
              AND c.mission.user IS NULL
            """)
    List<UUID> findMissionIdsAwaitingRewards();
}
