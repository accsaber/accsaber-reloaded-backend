package com.accsaber.backend.repository.campaign;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.campaign.CampaignChatMessage;

public interface CampaignChatMessageRepository extends JpaRepository<CampaignChatMessage, UUID> {

    @EntityGraph(attributePaths = { "user" })
    Page<CampaignChatMessage> findByCampaign_IdOrderByCreatedAtDesc(UUID campaignId, Pageable pageable);
}
