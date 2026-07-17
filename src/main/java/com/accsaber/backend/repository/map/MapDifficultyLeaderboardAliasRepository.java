package com.accsaber.backend.repository.map;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.accsaber.backend.model.entity.map.MapDifficultyLeaderboardAlias;

public interface MapDifficultyLeaderboardAliasRepository
        extends JpaRepository<MapDifficultyLeaderboardAlias, UUID> {

    List<MapDifficultyLeaderboardAlias> findByMapDifficulty_Id(UUID mapDifficultyId);

    @Query("""
            SELECT a.blLeaderboardId FROM MapDifficultyLeaderboardAlias a
            WHERE a.blLeaderboardId IS NOT NULL
              AND a.mapDifficulty.status = com.accsaber.backend.model.entity.map.MapDifficultyStatus.RANKED
              AND a.mapDifficulty.active = true
            """)
    List<String> findRankedBlLeaderboardIds();

    @Query("""
            SELECT a.ssLeaderboardId FROM MapDifficultyLeaderboardAlias a
            WHERE a.ssLeaderboardId IS NOT NULL
              AND a.mapDifficulty.status = com.accsaber.backend.model.entity.map.MapDifficultyStatus.RANKED
              AND a.mapDifficulty.active = true
            """)
    List<String> findRankedSsLeaderboardIds();
}
