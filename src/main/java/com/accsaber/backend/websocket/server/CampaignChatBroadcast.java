package com.accsaber.backend.websocket.server;

import java.util.UUID;

import com.accsaber.backend.model.dto.response.campaign.CampaignChatMessageResponse;

import lombok.Getter;

@Getter
public class CampaignChatBroadcast {

    private final String type = "chat";
    private final UUID campaignId;
    private final CampaignChatMessageResponse message;

    public CampaignChatBroadcast(UUID campaignId, CampaignChatMessageResponse message) {
        this.campaignId = campaignId;
        this.message = message;
    }
}
