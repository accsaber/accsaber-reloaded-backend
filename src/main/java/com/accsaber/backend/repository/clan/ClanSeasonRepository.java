package com.accsaber.backend.repository.clan;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.clan.ClanSeason;

import jakarta.persistence.LockModeType;

public interface ClanSeasonRepository extends JpaRepository<ClanSeason, UUID> {

    Optional<ClanSeason> findBySlug(String slug);

    Page<ClanSeason> findAllByOrderByStartsAtDesc(Pageable pageable);

    Optional<ClanSeason> findTopByOrderByEndsAtDesc();

    @Query("""
            SELECT s FROM ClanSeason s
            WHERE s.closedAt IS NULL AND s.startsAt <= :now AND s.endsAt > :now
            """)
    Optional<ClanSeason> findCurrent(@Param("now") Instant now);

    @Query("SELECT COUNT(s) > 0 FROM ClanSeason s WHERE s.closedAt IS NULL AND s.endsAt > :now")
    boolean existsOpenUntilAfter(@Param("now") Instant now);

    @Query("SELECT s.id FROM ClanSeason s WHERE s.closedAt IS NULL AND s.endsAt <= :now ORDER BY s.endsAt")
    List<UUID> findEndedUnclosedIds(@Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ClanSeason s WHERE s.id = :id")
    Optional<ClanSeason> findByIdForUpdate(@Param("id") UUID id);

    interface ContributorView {
        Long getUserId();

        double getContribution();
    }

    @Query(value = """
            SELECT x.user_id AS userId, SUM(x.contribution) AS contribution
            FROM (
                SELECT p.user_id, p.contribution, p.joined_at AS first_at
                FROM clan_war_participants p
                JOIN clan_wars w ON w.id = p.war_id
                WHERE w.season_id = :seasonId AND p.clan_id = :clanId AND p.lent_by_clan_id IS NULL
                UNION ALL
                SELECT c.user_id,
                       c.contribution / NULLIF(GREATEST(m.progress_count, m.progress_ap), 0) * :missionContribution,
                       c.first_at
                FROM clan_seasons s
                JOIN user_missions m ON m.clan_id = :clanId AND m.parent_mission_id IS NULL
                 AND m.status = 'completed' AND m.completed_at >= s.starts_at AND m.completed_at < s.ends_at
                JOIN mission_contributions c ON c.user_mission_id = m.id
                WHERE s.id = :seasonId
            ) x
            GROUP BY x.user_id
            HAVING SUM(x.contribution) > 0
            ORDER BY SUM(x.contribution) DESC, MIN(x.first_at) ASC
            """, nativeQuery = true)
    List<ContributorView> findContributors(@Param("seasonId") UUID seasonId, @Param("clanId") UUID clanId,
            @Param("missionContribution") double missionContribution);
}
