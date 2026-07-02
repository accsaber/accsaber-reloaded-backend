package com.accsaber.backend.repository.campaign;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.campaign.CampaignText;

public interface CampaignTextRepository extends JpaRepository<CampaignText, UUID> {

    List<CampaignText> findByCampaign_IdAndActiveTrue(UUID campaignId);

    Optional<CampaignText> findByIdAndActiveTrue(UUID id);

    long countByCampaign_IdAndActiveTrue(UUID campaignId);
}
