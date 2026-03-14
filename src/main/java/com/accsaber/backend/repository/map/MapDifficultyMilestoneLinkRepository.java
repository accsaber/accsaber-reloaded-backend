package com.accsaber.backend.repository.map;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.map.MapDifficultyMilestoneLink;

public interface MapDifficultyMilestoneLinkRepository extends JpaRepository<MapDifficultyMilestoneLink, UUID> {

    List<MapDifficultyMilestoneLink> findByMilestone_Id(UUID milestoneId);

    List<MapDifficultyMilestoneLink> findByMapDifficulty_Id(UUID mapDifficultyId);

    boolean existsByMilestone_Id(UUID milestoneId);
}
