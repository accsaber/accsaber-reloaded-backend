package com.accsaber.backend.service.campaign;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.accsaber.backend.model.event.CampaignChatMessageEvent;
import com.accsaber.backend.websocket.server.CampaignChatBroadcast;
import com.accsaber.backend.websocket.server.CampaignPresenceWebSocketHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CampaignChatBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(CampaignChatBroadcastService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final CampaignPresenceWebSocketHandler presenceHandler;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCampaignChatMessage(CampaignChatMessageEvent event) {
        try {
            String json = MAPPER.writeValueAsString(new CampaignChatBroadcast(event.campaignId(), event.message()));
            presenceHandler.broadcastChat(event.campaignId(), json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize campaign chat message for broadcast: {}", e.getMessage());
        }
    }
}
