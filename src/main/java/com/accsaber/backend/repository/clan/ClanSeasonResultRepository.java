package com.accsaber.backend.repository.clan;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.clan.ClanSeasonResult;

public interface ClanSeasonResultRepository extends JpaRepository<ClanSeasonResult, ClanSeasonResult.Key> {

    @Query(value = """
            SELECT r FROM ClanSeasonResult r JOIN FETCH r.clan
            WHERE r.season.id = :seasonId
            ORDER BY r.rank ASC
            """,
            countQuery = "SELECT COUNT(r) FROM ClanSeasonResult r WHERE r.season.id = :seasonId")
    Page<ClanSeasonResult> findPageBySeasonId(@Param("seasonId") UUID seasonId, Pageable pageable);

    Optional<ClanSeasonResult> findBySeason_IdAndClan_Id(UUID seasonId, UUID clanId);
}
