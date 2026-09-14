package com.accsaber.backend.service.clan.war;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.accsaber.backend.config.PlainDoubleJackson2Module;
import com.accsaber.backend.model.event.ClanFeedEvent;
import com.accsaber.backend.websocket.server.ClanFeedWebSocketHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClanFeedBroadcastService {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(PlainDoubleJackson2Module.create())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final ClanFeedWebSocketHandler feedHandler;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFeed(ClanFeedEvent event) {
        try {
            String json = MAPPER.writeValueAsString(event.payload());
            event.clanIds().forEach(clanId -> feedHandler.broadcast(clanId, json));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize clan feed payload: {}", e.getMessage());
        }
    }
}
