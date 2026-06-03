package com.accsaber.backend.service.mission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.accsaber.backend.model.event.MissionCompletedEvent;
import com.accsaber.backend.websocket.server.MissionFeedWebSocketHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MissionBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(MissionBroadcastService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final MissionFeedWebSocketHandler missionFeedHandler;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMissionCompleted(MissionCompletedEvent event) {
        try {
            String json = MAPPER.writeValueAsString(event.payload());
            missionFeedHandler.broadcast(json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize mission completion for broadcast: {}", e.getMessage());
        }
    }
}
