package com.accsaber.backend.model.dto.response.campaign;

import java.time.Instant;

import com.accsaber.backend.model.dto.response.common.PlayerRef;

public record CampaignProgressBroadcast(String type, PlayerRef player, CampaignResponse campaign,
        CampaignDifficultyResponse node, Instant completedAt) {
}
