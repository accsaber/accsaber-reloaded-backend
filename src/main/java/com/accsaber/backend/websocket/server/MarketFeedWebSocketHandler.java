package com.accsaber.backend.websocket.server;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class MarketFeedWebSocketHandler extends RoomWebSocketHandler<UUID> {

    @Override
    protected UUID resolveRoomKey(WebSocketSession session) {
        return uuidParam(session, "listingId");
    }
}
