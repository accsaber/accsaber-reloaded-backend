package com.accsaber.backend.repository.map;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.map.MapDifficultyMilestoneLink;

public interface MapDifficultyMilestoneLinkRepository extends JpaRepository<MapDifficultyMilestoneLink, UUID> {

    boolean existsByMapDifficulty_IdAndMilestone_Id(UUID mapDifficultyId, UUID milestoneId);

    void deleteByMilestone_IdAndMapDifficulty_IdIn(UUID milestoneId, List<UUID> mapDifficultyIds);
}
