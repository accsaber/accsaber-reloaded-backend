package com.accsaber.backend.websocket.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;

public abstract class BroadcastWebSocketHandler extends RoomWebSocketHandler<Boolean> {

    private static final int BUFFER_SIZE_LIMIT = 64 * 1024;

    private final Logger log = LoggerFactory.getLogger(getClass());

    protected BroadcastWebSocketHandler() {
        super(BUFFER_SIZE_LIMIT);
    }

    @Override
    protected Boolean resolveRoomKey(WebSocketSession session) {
        return Boolean.TRUE;
    }

    @Override
    protected void onSessionJoined(Boolean key, WebSocketSession session) {
        log.info("Feed client connected: {} (total: {})", session.getId(), sessions(key).size());
    }

    @Override
    protected void onSessionLeft(Boolean key, WebSocketSession session) {
        log.info("Feed client disconnected: {} (total: {})", session.getId(), sessions(key).size());
    }

    public void broadcast(String json) {
        broadcast(Boolean.TRUE, json);
    }
}
