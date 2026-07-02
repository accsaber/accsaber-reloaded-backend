package com.accsaber.backend.websocket.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class CampaignPresenceWebSocketHandlerTest {

    private static final UUID CAMPAIGN = UUID.randomUUID();

    private final CampaignPresenceWebSocketHandler handler = new CampaignPresenceWebSocketHandler();

    private WebSocketSession session(String id, long userId) {
        WebSocketSession s = mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(CampaignPresenceHandshakeInterceptor.ATTR_CAMPAIGN_ID, CAMPAIGN);
        attrs.put(CampaignPresenceHandshakeInterceptor.ATTR_USER_ID, userId);
        attrs.put(CampaignPresenceHandshakeInterceptor.ATTR_USER_NAME, "u" + userId);
        attrs.put(CampaignPresenceHandshakeInterceptor.ATTR_USER_AVATAR, "avatar" + userId);
        when(s.getId()).thenReturn(id);
        when(s.getAttributes()).thenReturn(attrs);
        when(s.isOpen()).thenReturn(true);
        return s;
    }

    private List<String> payloadsSentTo(WebSocketSession session) throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeast(0)).sendMessage(captor.capture());
        return captor.getAllValues().stream().map(TextMessage::getPayload).toList();
    }

    @Test
    void relaysToOthersButNotSender() throws Exception {
        WebSocketSession a = session("A", 1L);
        WebSocketSession b = session("B", 2L);
        handler.afterConnectionEstablished(a);
        handler.afterConnectionEstablished(b);
        clearInvocations(a, b);

        handler.handleTextMessage(b, new TextMessage("{\"type\":\"cursor\",\"x\":10,\"y\":20}"));

        assertThat(payloadsSentTo(a))
                .anyMatch(p -> p.contains("\"type\":\"cursor\"") && p.contains("\"actorUserId\":2"));
        assertThat(payloadsSentTo(b)).isEmpty();
    }

    @Test
    void joinNotifiesExistingMembers() throws Exception {
        WebSocketSession a = session("A", 1L);
        handler.afterConnectionEstablished(a);
        clearInvocations(a);

        WebSocketSession b = session("B", 2L);
        handler.afterConnectionEstablished(b);

        assertThat(payloadsSentTo(a))
                .anyMatch(p -> p.contains("presence_join") && p.contains("\"actorUserId\":2"));
    }

    @Test
    void leaveNotifiesRemainingMembers() throws Exception {
        WebSocketSession a = session("A", 1L);
        WebSocketSession b = session("B", 2L);
        handler.afterConnectionEstablished(a);
        handler.afterConnectionEstablished(b);
        clearInvocations(a, b);

        handler.afterConnectionClosed(b, CloseStatus.NORMAL);

        assertThat(payloadsSentTo(a))
                .anyMatch(p -> p.contains("presence_leave") && p.contains("\"actorUserId\":2"));
    }

    @Test
    void newJoinerReceivesSnapshotOfExistingMembers() throws Exception {
        WebSocketSession a = session("A", 1L);
        handler.afterConnectionEstablished(a);

        WebSocketSession b = session("B", 2L);
        handler.afterConnectionEstablished(b);

        assertThat(payloadsSentTo(b))
                .anyMatch(p -> p.contains("presence_state") && p.contains("\"userId\":1")
                        && p.contains("\"avatarUrl\":\"avatar1\""));
    }
}
