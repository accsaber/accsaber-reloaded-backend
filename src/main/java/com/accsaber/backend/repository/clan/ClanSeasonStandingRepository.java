package com.accsaber.backend.repository.clan;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.clan.ClanSeasonStanding;

public interface ClanSeasonStandingRepository extends JpaRepository<ClanSeasonStanding, ClanSeasonStanding.Key> {

    interface EarnedView {
        UUID getClanId();

        double getEarned();
    }

    @Query("""
            SELECT ss.clan.id AS clanId, ss.earned AS earned FROM ClanSeasonStanding ss
            WHERE ss.season.id = :seasonId AND ss.clan.id IN :clanIds
            """)
    List<EarnedView> findEarned(@Param("seasonId") UUID seasonId, @Param("clanIds") Collection<UUID> clanIds);

    interface RankingRow {
        UUID getClanId();

        double getBaseStanding();

        double getEarned();

        Instant getCreatedAt();
    }

    String LIVE_RANKING = """
            SELECT c.id AS clanId,
                   (c.roster_strength + c.ally_strength) * :perSkill AS baseStanding,
                   COALESCE(ss.earned, 0) AS earned,
                   c.created_at AS createdAt
            FROM clans c
            LEFT JOIN clan_season_standings ss ON ss.clan_id = c.id AND ss.season_id = :seasonId
            WHERE c.active
            ORDER BY (c.roster_strength + c.ally_strength) * :perSkill + COALESCE(ss.earned, 0) DESC,
                     c.created_at ASC, c.id ASC
            """;

    @Query(value = LIVE_RANKING,
            countQuery = "SELECT COUNT(*) FROM clans c WHERE c.active",
            nativeQuery = true)
    Page<RankingRow> findLiveRanking(@Param("seasonId") UUID seasonId, @Param("perSkill") double perSkill,
            Pageable pageable);

    @Query(value = LIVE_RANKING, nativeQuery = true)
    List<RankingRow> findFullLiveRanking(@Param("seasonId") UUID seasonId, @Param("perSkill") double perSkill);

    @Query(value = """
            SELECT COUNT(*) + 1 FROM clans c
            LEFT JOIN clan_season_standings ss ON ss.clan_id = c.id AND ss.season_id = :seasonId
            WHERE c.active
              AND ((c.roster_strength + c.ally_strength) * :perSkill + COALESCE(ss.earned, 0) > :standing
                   OR ((c.roster_strength + c.ally_strength) * :perSkill + COALESCE(ss.earned, 0) = :standing
                       AND (c.created_at, c.id) < (CAST(:createdAt AS timestamptz), CAST(:clanId AS uuid))))
            """, nativeQuery = true)
    long findLiveRank(@Param("seasonId") UUID seasonId, @Param("perSkill") double perSkill,
            @Param("standing") double standing, @Param("createdAt") Instant createdAt, @Param("clanId") UUID clanId);

    @Modifying
    @Query(value = """
            INSERT INTO clan_season_standings (season_id, clan_id) VALUES (:seasonId, :clanId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int ensureRow(@Param("seasonId") UUID seasonId, @Param("clanId") UUID clanId);

    @Query(value = """
            SELECT earned FROM clan_season_standings
            WHERE season_id = :seasonId AND clan_id = :clanId
            FOR UPDATE
            """, nativeQuery = true)
    double lockEarned(@Param("seasonId") UUID seasonId, @Param("clanId") UUID clanId);

    @Modifying
    @Query(value = """
            UPDATE clan_season_standings SET earned = earned + :amount, updated_at = NOW()
            WHERE season_id = :seasonId AND clan_id = :clanId
            """, nativeQuery = true)
    int addEarned(@Param("seasonId") UUID seasonId, @Param("clanId") UUID clanId, @Param("amount") double amount);
}
