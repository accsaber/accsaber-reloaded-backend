package com.accsaber.backend.repository.clan;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.clan.ClanSeasonReward;

public interface ClanSeasonRewardRepository extends JpaRepository<ClanSeasonReward, UUID> {

    @Query("""
            SELECT r FROM ClanSeasonReward r JOIN FETCH r.item i JOIN FETCH i.type t LEFT JOIN FETCH t.parentType
            WHERE r.season.id = :seasonId
            ORDER BY r.rankFrom ASC
            """)
    List<ClanSeasonReward> findBySeasonId(@Param("seasonId") UUID seasonId);
}
