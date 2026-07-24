package com.accsaber.backend.websocket.server;

import java.time.Instant;

import com.accsaber.backend.model.dto.response.campaign.CampaignDifficultyResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignLeaderboardPlayer;
import com.accsaber.backend.model.dto.response.campaign.CampaignResponse;

public record CampaignProgressBroadcast(String type, CampaignLeaderboardPlayer player, CampaignResponse campaign,
        CampaignDifficultyResponse node, Instant completedAt) {
}
