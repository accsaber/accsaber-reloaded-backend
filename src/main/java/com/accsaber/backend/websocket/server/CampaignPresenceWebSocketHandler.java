package com.accsaber.backend.websocket.server;

import java.io.IOException;
import java.util.LinkedHashMap;
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class CampaignPresenceWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(CampaignPresenceWebSocketHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int SEND_TIME_LIMIT = 5_000;
    private static final int BUFFER_SIZE_LIMIT = 32 * 1024;
    private static final int MAX_MESSAGE_LENGTH = 4096;

    private final Map<UUID, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession rawSession) {
        WebSocketSession session = new ConcurrentWebSocketSessionDecorator(
                rawSession, SEND_TIME_LIMIT, BUFFER_SIZE_LIMIT);
        UUID campaignId = campaignId(session);
        if (campaignId == null) {
            return;
        }
        rooms.computeIfAbsent(campaignId, k -> ConcurrentHashMap.newKeySet()).add(session);
        sendSnapshot(session, campaignId);
        broadcastPresence(campaignId, session, "presence_join");
        log.info("Presence client joined campaign {} (user {})", campaignId, userId(session));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        UUID campaignId = campaignId(session);
        if (campaignId == null) {
            return;
        }
        String payload = message.getPayload();
        if (payload.length() > MAX_MESSAGE_LENGTH) {
            return;
        }
        CampaignPresenceMessage msg;
        try {
            msg = MAPPER.readValue(payload, CampaignPresenceMessage.class);
        } catch (IOException e) {
            return;
        }
        if (msg.getType() == null || msg.getType().isBlank()) {
            return;
        }
        stamp(msg, session, campaignId);
        String json = write(msg);
        if (json != null) {
            sendToRoom(campaignId, session, json);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        remove(session);
    }

    private void remove(WebSocketSession session) {
        UUID campaignId = campaignId(session);
        if (campaignId == null) {
            return;
        }
        Set<WebSocketSession> room = rooms.get(campaignId);
        if (room == null) {
            return;
        }
        room.removeIf(s -> s.getId().equals(session.getId()));
        broadcastPresence(campaignId, session, "presence_leave");
        if (room.isEmpty()) {
            rooms.remove(campaignId, room);
        }
    }

    private void sendSnapshot(WebSocketSession session, UUID campaignId) {
        Set<WebSocketSession> room = rooms.get(campaignId);
        if (room == null) {
            return;
        }
        Map<Long, CampaignPresenceMessage.Member> byUser = new LinkedHashMap<>();
        for (WebSocketSession peer : room) {
            if (peer.getId().equals(session.getId())) {
                continue;
            }
            Long uid = userId(peer);
            if (uid != null) {
                byUser.putIfAbsent(uid,
                        new CampaignPresenceMessage.Member(uid, userName(peer), userAvatar(peer)));
            }
        }
        CampaignPresenceMessage msg = new CampaignPresenceMessage();
        msg.setType("presence_state");
        msg.setCampaignId(campaignId);
        msg.setTs(System.currentTimeMillis());
        msg.setMembers(byUser.values().stream().toList());
        String json = write(msg);
        if (json != null) {
            sendOne(session, json);
        }
    }

    private void broadcastPresence(UUID campaignId, WebSocketSession actorSession, String type) {
        CampaignPresenceMessage msg = new CampaignPresenceMessage();
        msg.setType(type);
        stamp(msg, actorSession, campaignId);
        String json = write(msg);
        if (json != null) {
            sendToRoom(campaignId, actorSession, json);
        }
    }

    private void stamp(CampaignPresenceMessage msg, WebSocketSession session, UUID campaignId) {
        msg.setCampaignId(campaignId);
        msg.setActorUserId(userId(session));
        msg.setActorName(userName(session));
        msg.setActorAvatarUrl(userAvatar(session));
        msg.setTs(System.currentTimeMillis());
        msg.setMembers(null);
    }

    private void sendToRoom(UUID campaignId, WebSocketSession exclude, String json) {
        Set<WebSocketSession> room = rooms.get(campaignId);
        if (room == null) {
            return;
        }
        TextMessage text = new TextMessage(json);
        for (WebSocketSession peer : room) {
            if (exclude != null && peer.getId().equals(exclude.getId())) {
                continue;
            }
            sendOne(peer, text, room);
        }
    }

    private void sendOne(WebSocketSession session, String json) {
        sendOne(session, new TextMessage(json), null);
    }

    private void sendOne(WebSocketSession session, TextMessage text, Set<WebSocketSession> room) {
        try {
            if (session.isOpen()) {
                session.sendMessage(text);
            }
        } catch (IOException | RuntimeException e) {
            if (room != null) {
                room.remove(session);
            }
            log.debug("Failed to send presence to session {}: {}", session.getId(), e.getMessage());
        }
    }

    private String write(CampaignPresenceMessage msg) {
        try {
            return MAPPER.writeValueAsString(msg);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize presence message: {}", e.getMessage());
            return null;
        }
    }

    private static UUID campaignId(WebSocketSession session) {
        return (UUID) session.getAttributes().get(CampaignPresenceHandshakeInterceptor.ATTR_CAMPAIGN_ID);
    }

    private static Long userId(WebSocketSession session) {
        return (Long) session.getAttributes().get(CampaignPresenceHandshakeInterceptor.ATTR_USER_ID);
    }

    private static String userName(WebSocketSession session) {
        return (String) session.getAttributes().get(CampaignPresenceHandshakeInterceptor.ATTR_USER_NAME);
    }

    private static String userAvatar(WebSocketSession session) {
        return (String) session.getAttributes().get(CampaignPresenceHandshakeInterceptor.ATTR_USER_AVATAR);
    }
}
