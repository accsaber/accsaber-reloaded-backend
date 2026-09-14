package com.accsaber.backend.websocket.server;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.HashMap;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
class ClanFeedWebSocketHandlerTest {

    private static final UUID CLAN = UUID.randomUUID();

    @Mock
    private WebSocketSession watching;
    @Mock
    private WebSocketSession elsewhere;

    private final ClanFeedWebSocketHandler handler = new ClanFeedWebSocketHandler();

    private void connect(WebSocketSession session, String query) throws Exception {
        when(session.getUri()).thenReturn(URI.create("ws://localhost/ws/clans/feed" + query));
        lenient().when(session.getAttributes()).thenReturn(new HashMap<>());
        lenient().when(session.getId()).thenReturn(query);
        lenient().when(session.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(session);
    }

    @Test
    void aBroadcastOnlyReachesTheClanItWasSentTo() throws Exception {
        connect(watching, "?clanId=" + CLAN);
        connect(elsewhere, "?clanId=" + UUID.randomUUID());

        handler.broadcast(CLAN, "{}");

        verify(watching).sendMessage(new TextMessage("{}"));
        verify(elsewhere, never()).sendMessage(any());
    }

    @Test
    void aMissingOrMalformedClanIdIsTurnedAway() throws Exception {
        connect(watching, "");
        connect(elsewhere, "?clanId=owls");

        verify(watching).close(CloseStatus.BAD_DATA);
        verify(elsewhere).close(CloseStatus.BAD_DATA);
    }
}
