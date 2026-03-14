package com.accsaber.backend.websocket.server;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class ScoreFeedWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ScoreFeedWebSocketHandler.class);
    private static final int SEND_TIME_LIMIT = 5_000;
    private static final int BUFFER_SIZE_LIMIT = 64 * 1024;

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT, BUFFER_SIZE_LIMIT));
        log.info("Score feed client connected: {} (total: {})", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.removeIf(s -> s.getId().equals(session.getId()));
        log.info("Score feed client disconnected: {} (total: {})", session.getId(), sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.removeIf(s -> s.getId().equals(session.getId()));
        log.warn("Transport error for session {}: {}", session.getId(), exception.getMessage());
    }

    public void broadcast(String json) {
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            } catch (IOException | RuntimeException e) {
                log.warn("Failed to send to session {}: {}", session.getId(), e.getMessage());
                sessions.remove(session);
            }
        }
    }
}
