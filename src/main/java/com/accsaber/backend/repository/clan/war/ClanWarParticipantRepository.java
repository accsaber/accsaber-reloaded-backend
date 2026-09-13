package com.accsaber.backend.repository.clan.war;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.clan.war.ClanWarParticipant;

public interface ClanWarParticipantRepository extends JpaRepository<ClanWarParticipant, ClanWarParticipant.Key> {

    interface ContributorView {
        Long getUserId();

        double getContribution();
    }

    @Query(value = """
            SELECT p.user_id AS userId, SUM(p.contribution) AS contribution
            FROM clan_war_participants p
            JOIN clan_wars w ON w.id = p.war_id
            WHERE w.season_id = :seasonId AND p.clan_id = :clanId AND p.lent_by_clan_id IS NULL
            GROUP BY p.user_id
            HAVING SUM(p.contribution) > 0
            ORDER BY SUM(p.contribution) DESC, MIN(p.joined_at) ASC
            """, nativeQuery = true)
    List<ContributorView> findSeasonContributors(@Param("seasonId") UUID seasonId, @Param("clanId") UUID clanId);
}
