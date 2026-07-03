package com.accsaber.backend.model.event;

import java.util.UUID;

import com.accsaber.backend.model.dto.response.campaign.CampaignChatMessageResponse;

public record CampaignChatMessageEvent(UUID campaignId, CampaignChatMessageResponse message) {
}
