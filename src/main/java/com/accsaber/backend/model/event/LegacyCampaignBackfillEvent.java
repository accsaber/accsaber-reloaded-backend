package com.accsaber.backend.model.event;

import java.util.UUID;

public record LegacyCampaignBackfillEvent(Long userId, UUID campaignId) {
}
