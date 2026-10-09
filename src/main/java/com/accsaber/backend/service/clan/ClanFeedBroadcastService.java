package com.accsaber.backend.service.clan;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.accsaber.backend.config.PlainDoubleJackson2Module;
import com.accsaber.backend.model.event.ClanFeedEvent;
import com.accsaber.backend.websocket.server.ClanFeedWebSocketHandler;
import com.accsaber.backend.websocket.server.ClanGlobalFeedWebSocketHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClanFeedBroadcastService {

    private static final ObjectMapper MAPPER = PlainDoubleJackson2Module.mapper();

    private final ClanFeedWebSocketHandler feedHandler;
    private final ClanGlobalFeedWebSocketHandler globalFeedHandler;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFeed(ClanFeedEvent event) {
        try {
            String json = MAPPER.writeValueAsString(event.payload());
            if (event.clanIds().isEmpty()) {
                globalFeedHandler.broadcast(json);
            }
            event.clanIds().forEach(clanId -> feedHandler.broadcast(clanId, json));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize clan feed payload: {}", e.getMessage());
        }
    }
}
