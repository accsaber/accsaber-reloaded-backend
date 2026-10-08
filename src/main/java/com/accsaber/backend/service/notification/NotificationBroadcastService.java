package com.accsaber.backend.service.notification;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.accsaber.backend.config.PlainDoubleJackson2Module;
import com.accsaber.backend.model.event.NotificationCreatedEvent;
import com.accsaber.backend.websocket.server.NotificationWebSocketHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationBroadcastService {

    private static final ObjectMapper MAPPER = PlainDoubleJackson2Module.mapper();

    private final NotificationWebSocketHandler notificationHandler;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        try {
            notificationHandler.sendToUser(event.userId(), MAPPER.writeValueAsString(event.notification()));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize notification for broadcast: {}", e.getMessage());
        }
    }
}
