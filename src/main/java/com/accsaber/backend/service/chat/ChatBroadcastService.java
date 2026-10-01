package com.accsaber.backend.service.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.accsaber.backend.config.PlainDoubleJackson2Module;
import com.accsaber.backend.model.event.ChatMessageEvent;
import com.accsaber.backend.websocket.server.ChatBroadcast;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Service
public class ChatBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(ChatBroadcastService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(PlainDoubleJackson2Module.create())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatMessage(ChatMessageEvent event) {
        try {
            String json = MAPPER.writeValueAsString(ChatBroadcast.of(event.channelId(), event.message()));
            event.channel().broadcast(event.channelId(), json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize chat message for broadcast: {}", e.getMessage());
        }
    }
}
