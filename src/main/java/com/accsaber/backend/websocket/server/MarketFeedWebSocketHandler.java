package com.accsaber.backend.websocket.server;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class MarketFeedWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MarketFeedWebSocketHandler.class);
    private static final int SEND_TIME_LIMIT = 5_000;
    private static final int BUFFER_SIZE_LIMIT = 32 * 1024;
    private static final String ATTR_LISTING_ID = "marketListingId";

    private final Map<UUID, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        UUID listingId = extractListingId(session.getUri());
        if (listingId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        session.getAttributes().put(ATTR_LISTING_ID, listingId);
        rooms.computeIfAbsent(listingId, k -> ConcurrentHashMap.newKeySet())
                .add(new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT, BUFFER_SIZE_LIMIT));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("Market feed transport error for session {}: {}", session.getId(), exception.getMessage());
        remove(session);
    }

    public void broadcast(UUID listingId, String json) {
        Set<WebSocketSession> room = rooms.get(listingId);
        if (room == null || room.isEmpty()) {
            return;
        }
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : room) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            } catch (IOException | RuntimeException e) {
                log.warn("Failed to send to market session {}: {}", session.getId(), e.getMessage());
                room.remove(session);
            }
        }
    }

    private void remove(WebSocketSession session) {
        UUID listingId = (UUID) session.getAttributes().get(ATTR_LISTING_ID);
        if (listingId == null) {
            return;
        }
        Set<WebSocketSession> room = rooms.get(listingId);
        if (room == null) {
            return;
        }
        room.removeIf(s -> s.getId().equals(session.getId()));
        if (room.isEmpty()) {
            rooms.remove(listingId, room);
        }
    }

    private UUID extractListingId(URI uri) {
        if (uri == null) {
            return null;
        }
        String raw = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("listingId");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
