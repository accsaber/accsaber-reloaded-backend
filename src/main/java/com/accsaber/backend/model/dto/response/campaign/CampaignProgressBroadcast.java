package com.accsaber.backend.model.dto.response.campaign;

import java.time.Instant;

import com.accsaber.backend.model.dto.response.statistics.UserIdentity;

public record CampaignProgressBroadcast(String type, UserIdentity player, CampaignResponse campaign,
        CampaignDifficultyResponse node, Instant completedAt) {
}
