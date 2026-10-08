package com.accsaber.backend.service.item;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.accsaber.backend.config.PlainDoubleJackson2Module;
import com.accsaber.backend.model.event.CrateOpenedEvent;
import com.accsaber.backend.websocket.server.CrateFeedWebSocketHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrateBroadcastService {

    private static final ObjectMapper MAPPER = PlainDoubleJackson2Module.mapper();

    private final CrateFeedWebSocketHandler crateFeedHandler;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCrateOpened(CrateOpenedEvent event) {
        try {
            crateFeedHandler.broadcast(MAPPER.writeValueAsString(event.payload()));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize crate open for broadcast: {}", e.getMessage());
        }
    }
}
