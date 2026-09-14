package com.accsaber.backend.websocket.server;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class ClanFeedWebSocketHandler extends RoomWebSocketHandler<UUID> {

    @Override
    protected UUID resolveRoomKey(WebSocketSession session) {
        if (session.getUri() == null) {
            return null;
        }
        String clanId = UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().getFirst("clanId");
        try {
            return clanId == null ? null : UUID.fromString(clanId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void broadcast(UUID clanId, String json) {
        sendToRoom(clanId, json);
    }
}
