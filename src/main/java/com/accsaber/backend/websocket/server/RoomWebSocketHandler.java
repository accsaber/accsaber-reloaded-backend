package com.accsaber.backend.websocket.server;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import com.accsaber.backend.util.Slugs;

public abstract class RoomWebSocketHandler<K> extends TextWebSocketHandler {

    private static final String ATTR_ROOM_KEY = "roomKey";
    private static final int SEND_TIME_LIMIT = 5_000;
    private static final int DEFAULT_BUFFER_SIZE_LIMIT = 32 * 1024;

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Map<K, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();
    private final int bufferSizeLimit;

    protected RoomWebSocketHandler() {
        this(DEFAULT_BUFFER_SIZE_LIMIT);
    }

    protected RoomWebSocketHandler(int bufferSizeLimit) {
        this.bufferSizeLimit = bufferSizeLimit;
    }

    protected abstract K resolveRoomKey(WebSocketSession session);

    protected static UUID uuidParam(WebSocketSession session, String name) {
        return session.getUri() == null ? null
                : Slugs.uuidOrNull(UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams()
                        .getFirst(name));
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        K key = resolveRoomKey(session);
        if (key == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        session.getAttributes().put(ATTR_ROOM_KEY, key);
        WebSocketSession decorated = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT,
                bufferSizeLimit);
        AtomicBoolean opened = new AtomicBoolean();
        rooms.compute(key, (k, room) -> {
            Set<WebSocketSession> target = room == null ? ConcurrentHashMap.newKeySet() : room;
            opened.set(target.isEmpty());
            target.add(decorated);
            return target;
        });
        if (opened.get()) {
            onRoomOpened(key);
        }
        onSessionJoined(key, decorated);
    }

    protected void onRoomOpened(K key) {
    }

    protected void onRoomEmptied(K key) {
    }

    protected void onSessionJoined(K key, WebSocketSession session) {
    }

    protected void onSessionLeft(K key, WebSocketSession session) {
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

    protected boolean hasSessions(K key) {
        Set<WebSocketSession> room = rooms.get(key);
        return room != null && !room.isEmpty();
    }

    protected Set<WebSocketSession> sessions(K key) {
        Set<WebSocketSession> room = rooms.get(key);
        return room == null ? Set.of() : room;
    }

    protected void closeWhere(K key, Predicate<WebSocketSession> predicate) {
        Set<WebSocketSession> room = rooms.get(key);
        if (room == null) {
            return;
        }
        for (WebSocketSession session : room) {
            if (predicate.test(session)) {
                try {
                    session.close(CloseStatus.POLICY_VIOLATION);
                } catch (IOException e) {
                    log.warn("Failed to close session {}: {}", session.getId(), e.getMessage());
                }
                room.remove(session);
            }
        }
    }

    public void broadcast(K key, String json) {
        broadcast(key, json, null);
    }

    protected void broadcast(K key, String json, WebSocketSession exclude) {
        Set<WebSocketSession> room = rooms.get(key);
        if (room == null || room.isEmpty()) {
            return;
        }
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : room) {
            if (exclude != null && session.getId().equals(exclude.getId())) {
                continue;
            }
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
        AtomicBoolean present = new AtomicBoolean();
        AtomicBoolean emptied = new AtomicBoolean();
        rooms.computeIfPresent(key, (k, room) -> {
            present.set(true);
            room.removeIf(s -> s.getId().equals(session.getId()));
            emptied.set(room.isEmpty());
            return room.isEmpty() ? null : room;
        });
        if (present.get()) {
            onSessionLeft(key, session);
        }
        if (emptied.get()) {
            onRoomEmptied(key);
        }
    }
}
