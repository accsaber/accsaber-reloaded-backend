package com.accsaber.backend.websocket.server;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import com.accsaber.backend.model.event.PresenceChangedEvent;

@ExtendWith(MockitoExtension.class)
class NotificationWebSocketHandlerTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private WebSocketSession session(String id, long userId) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(NotificationHandshakeInterceptor.ATTR_USER_ID, userId);
        lenient().when(session.getAttributes()).thenReturn(attributes);
        lenient().when(session.getId()).thenReturn(id);
        return session;
    }

    @Test
    void aPlayerGoesOnlineWithTheirFirstTabAndOfflineWithTheirLast() throws Exception {
        NotificationWebSocketHandler handler = new NotificationWebSocketHandler(eventPublisher);
        WebSocketSession firstTab = session("a", 7L);
        WebSocketSession secondTab = session("b", 7L);

        handler.afterConnectionEstablished(firstTab);
        handler.afterConnectionEstablished(secondTab);
        verify(eventPublisher).publishEvent(new PresenceChangedEvent(7L, true));

        handler.afterConnectionClosed(firstTab, CloseStatus.NORMAL);
        verify(eventPublisher, never()).publishEvent(new PresenceChangedEvent(7L, false));

        handler.afterConnectionClosed(secondTab, CloseStatus.NORMAL);
        verify(eventPublisher).publishEvent(new PresenceChangedEvent(7L, false));

        handler.afterConnectionClosed(secondTab, CloseStatus.NORMAL);
        verifyNoMoreInteractions(eventPublisher);
    }
}
