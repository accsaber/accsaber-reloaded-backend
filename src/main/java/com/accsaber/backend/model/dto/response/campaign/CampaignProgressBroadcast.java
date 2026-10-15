package com.accsaber.backend.model.dto.response.campaign;

import java.time.Instant;

import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;

public record CampaignProgressBroadcast(String type, Player player, CampaignResponse campaign,
        CampaignDifficultyResponse node, Instant completedAt) {

    public record Player(String userId, String userName, String avatarUrl, String cdnAvatarUrl,
            PublicClanResponse clan, String country) {
    }
}
