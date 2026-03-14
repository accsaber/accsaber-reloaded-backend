package com.accsaber.backend.repository.campaign;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.campaign.CampaignMapPath;

public interface CampaignMapPathRepository extends JpaRepository<CampaignMapPath, UUID> {

    List<CampaignMapPath> findByCampaignMap_IdAndActiveTrue(UUID campaignMapId);

    List<CampaignMapPath> findByCampaignMap_Campaign_IdAndActiveTrue(UUID campaignId);
}
