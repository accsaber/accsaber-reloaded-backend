package com.accsaber.backend.model.event;

import java.time.Instant;
import java.util.UUID;

public record CampaignNodeCompletedEvent(Long userId, UUID campaignId, UUID nodeId, Instant completedAt) {
}
