package com.accsaber.backend.websocket.server;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.accsaber.backend.config.PlainDoubleJackson2Module;
import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class CampaignPresenceWebSocketHandler extends RoomWebSocketHandler<UUID> {

    private static final Logger log = LoggerFactory.getLogger(CampaignPresenceWebSocketHandler.class);
    private static final ObjectMapper MAPPER = PlainDoubleJackson2Module.mapper();
    private static final int MAX_MESSAGE_LENGTH = 4096;

    @Override
    protected UUID resolveRoomKey(WebSocketSession session) {
        return campaignId(session);
    }

    @Override
    protected void onSessionJoined(UUID campaignId, WebSocketSession session) {
        sendSnapshot(session, campaignId);
        broadcastPresence(campaignId, session, "presence_join");
        log.info("Presence client joined campaign {} (user {})", campaignId, userId(session));
    }

    @Override
    protected void onSessionLeft(UUID campaignId, WebSocketSession session) {
        broadcastPresence(campaignId, session, "presence_leave");
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
            broadcast(campaignId, json, session);
        }
    }

    public void broadcastChat(UUID campaignId, String json) {
        broadcast(campaignId, json);
    }

    private void sendSnapshot(WebSocketSession session, UUID campaignId) {
        Map<Long, PlayerRef> byUser = new LinkedHashMap<>();
        for (WebSocketSession peer : sessions(campaignId)) {
            if (peer.getId().equals(session.getId())) {
                continue;
            }
            Long uid = userId(peer);
            if (uid != null) {
                byUser.putIfAbsent(uid, player(peer));
            }
        }
        CampaignPresenceMessage msg = new CampaignPresenceMessage();
        msg.setType("presence_state");
        msg.setCampaignId(campaignId);
        msg.setTs(System.currentTimeMillis());
        msg.setMembers(byUser.values().stream().toList());
        String json = write(msg);
        if (json == null) {
            return;
        }
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException | RuntimeException e) {
            log.debug("Failed to send presence to session {}: {}", session.getId(), e.getMessage());
        }
    }

    private void broadcastPresence(UUID campaignId, WebSocketSession actorSession, String type) {
        CampaignPresenceMessage msg = new CampaignPresenceMessage();
        msg.setType(type);
        stamp(msg, actorSession, campaignId);
        String json = write(msg);
        if (json != null) {
            broadcast(campaignId, json, actorSession);
        }
    }

    private void stamp(CampaignPresenceMessage msg, WebSocketSession session, UUID campaignId) {
        msg.setCampaignId(campaignId);
        msg.setActor(player(session));
        msg.setTs(System.currentTimeMillis());
        msg.setMembers(null);
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

    private static PlayerRef player(WebSocketSession session) {
        return (PlayerRef) session.getAttributes().get(CampaignPresenceHandshakeInterceptor.ATTR_PLAYER);
    }
}
