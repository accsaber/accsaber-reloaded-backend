package com.accsaber.backend.websocket.server;

import java.net.URI;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class MarketFeedWebSocketHandler extends RoomWebSocketHandler<UUID> {

    @Override
    protected UUID resolveRoomKey(WebSocketSession session) {
        URI uri = session.getUri();
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

    public void broadcast(UUID listingId, String json) {
        sendToRoom(listingId, json);
    }
}
