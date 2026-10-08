package com.accsaber.backend.service.market;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.accsaber.backend.config.PlainDoubleJackson2Module;
import com.accsaber.backend.model.event.MarketListingEvent;
import com.accsaber.backend.websocket.server.MarketFeedWebSocketHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketBroadcastService {

    private static final ObjectMapper MAPPER = PlainDoubleJackson2Module.mapper();

    private final MarketFeedWebSocketHandler marketFeedHandler;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMarketListingEvent(MarketListingEvent event) {
        try {
            marketFeedHandler.broadcast(event.listingId(), MAPPER.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize market event for broadcast: {}", e.getMessage());
        }
    }
}
