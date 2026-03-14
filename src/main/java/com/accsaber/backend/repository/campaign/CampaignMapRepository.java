package com.accsaber.backend.repository.campaign;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.campaign.CampaignMap;

public interface CampaignMapRepository extends JpaRepository<CampaignMap, UUID> {

    List<CampaignMap> findByCampaign_IdAndActiveTrue(UUID campaignId);

    Optional<CampaignMap> findByIdAndActiveTrue(UUID id);

    Optional<CampaignMap> findByCampaign_IdAndMapDifficulty_IdAndActiveTrue(UUID campaignId, UUID mapDifficultyId);
}
