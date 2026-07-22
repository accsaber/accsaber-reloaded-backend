package com.accsaber.backend.websocket.server;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

public abstract class RoomWebSocketHandler<K> extends TextWebSocketHandler {

    private static final String ATTR_ROOM_KEY = "roomKey";
    private static final int SEND_TIME_LIMIT = 5_000;
    private static final int BUFFER_SIZE_LIMIT = 32 * 1024;

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Map<K, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    protected abstract K resolveRoomKey(WebSocketSession session);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        K key = resolveRoomKey(session);
        if (key == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        session.getAttributes().put(ATTR_ROOM_KEY, key);
        rooms.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet())
                .add(new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT, BUFFER_SIZE_LIMIT));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("Transport error for session {}: {}", session.getId(), exception.getMessage());
        remove(session);
    }

    protected void sendToRoom(K key, String json) {
        Set<WebSocketSession> room = rooms.get(key);
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
                log.warn("Failed to send to session {}: {}", session.getId(), e.getMessage());
                room.remove(session);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void remove(WebSocketSession session) {
        K key = (K) session.getAttributes().get(ATTR_ROOM_KEY);
        if (key == null) {
            return;
        }
        Set<WebSocketSession> room = rooms.get(key);
        if (room == null) {
            return;
        }
        room.removeIf(s -> s.getId().equals(session.getId()));
        if (room.isEmpty()) {
            rooms.remove(key, room);
        }
    }
}
