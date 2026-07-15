package com.accsaber.backend.model.event;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.entity.campaign.CampaignStatus;

public record CampaignCompletedEvent(Long userId, UUID campaignId, CampaignStatus campaignStatus,
        Instant completedAt) {
}
