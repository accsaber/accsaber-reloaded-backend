package com.accsaber.backend.websocket.server;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
class ClanChatWebSocketHandlerTest {

    private static final UUID CLAN = UUID.randomUUID();

    @Mock
    private WebSocketSession stays;
    @Mock
    private WebSocketSession kicked;

    private final ClanChatWebSocketHandler handler = new ClanChatWebSocketHandler();

    private void connect(WebSocketSession session, String id, long userId) throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ClanChatHandshakeInterceptor.ATTR_CLAN_ID, CLAN);
        attributes.put(ClanChatHandshakeInterceptor.ATTR_USER_ID, userId);
        lenient().when(session.getAttributes()).thenReturn(attributes);
        lenient().when(session.getId()).thenReturn(id);
        lenient().when(session.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(session);
    }

    @Test
    void aPlayerNoLongerInTheClanIsDisconnectedAndStopsHearingTheChat() throws Exception {
        connect(stays, "a", 1L);
        connect(kicked, "b", 2L);

        handler.keepOnly(CLAN, () -> List.of(1L));
        handler.broadcast(CLAN, "{}");

        verify(kicked).close(CloseStatus.POLICY_VIOLATION);
        verify(stays, never()).close(any());
        verify(stays).sendMessage(new TextMessage("{}"));
        verify(kicked, never()).sendMessage(any());
    }

    @Test
    void anEmptyRoomNeverAsksWhoIsStillAMember() {
        handler.keepOnly(UUID.randomUUID(), () -> {
            throw new AssertionError("members were loaded for a room nobody is in");
        });
    }
}
