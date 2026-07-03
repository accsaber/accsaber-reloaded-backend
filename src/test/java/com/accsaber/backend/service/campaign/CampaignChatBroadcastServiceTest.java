package com.accsaber.backend.service.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.dto.response.campaign.CampaignChatMessageResponse;
import com.accsaber.backend.model.event.CampaignChatMessageEvent;
import com.accsaber.backend.websocket.server.CampaignPresenceWebSocketHandler;

@ExtendWith(MockitoExtension.class)
class CampaignChatBroadcastServiceTest {

    @Mock
    private CampaignPresenceWebSocketHandler presenceHandler;

    @InjectMocks
    private CampaignChatBroadcastService service;

    @Test
    void broadcastsTypedChatEnvelopeWithIsoTimestampToCampaignRoom() {
        UUID campaignId = UUID.randomUUID();
        CampaignChatMessageResponse message = CampaignChatMessageResponse.builder()
                .id(UUID.randomUUID())
                .campaignId(campaignId)
                .authorId(50L)
                .authorName("Tester")
                .content("hello team")
                .createdAt(Instant.parse("2026-07-03T21:00:00Z"))
                .build();

        service.onCampaignChatMessage(new CampaignChatMessageEvent(campaignId, message));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(presenceHandler).broadcastChat(eq(campaignId), json.capture());
        assertThat(json.getValue())
                .contains("\"type\":\"chat\"")
                .contains("hello team")
                .contains("2026-07-03T21:00:00Z")
                .contains(campaignId.toString());
    }
}
