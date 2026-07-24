package com.accsaber.backend.websocket.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class CampaignProgressWebSocketHandlerTest {

        private CampaignProgressWebSocketHandler handler;

        @BeforeEach
        void setUp() {
                handler = new CampaignProgressWebSocketHandler();
        }

        private WebSocketSession mockSession(String id, boolean open) {
                WebSocketSession session = mock(WebSocketSession.class);
                when(session.getId()).thenReturn(id);
                when(session.isOpen()).thenReturn(open);
                return session;
        }

        @Nested
        class ConnectionLifecycle {

                @Test
                void afterConnectionEstablished_addsSession() throws Exception {
                        WebSocketSession session = mockSession("s1", true);

                        handler.afterConnectionEstablished(session);
                        handler.broadcast("{\"type\":\"node_completed\"}");
                        assertThat(session.getId()).isEqualTo("s1");
                }

                @Test
                void afterConnectionClosed_removesSession() throws Exception {
                        WebSocketSession session = mockSession("s1", true);
                        handler.afterConnectionEstablished(session);

                        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

                        handler.broadcast("{\"type\":\"node_completed\"}");
                        verify(session, never()).sendMessage(new TextMessage("{\"type\":\"node_completed\"}"));
                }

                @Test
                void handleTransportError_removesSession() throws Exception {
                        WebSocketSession session = mockSession("s1", true);
                        handler.afterConnectionEstablished(session);

                        handler.handleTransportError(session, new RuntimeException("connection lost"));

                        handler.broadcast("{\"type\":\"node_completed\"}");
                        verify(session, never()).sendMessage(new TextMessage("{\"type\":\"node_completed\"}"));
                }
        }

        @Nested
        class Broadcast {

                @Test
                void broadcast_noSessions_doesNotThrow() {
                        handler.broadcast("{\"type\":\"campaign_completed\"}");
                }

                @Test
                void broadcast_multipleSessions_sendsToAll() throws Exception {
                        WebSocketSession s1 = mockSession("s1", true);
                        WebSocketSession s2 = mockSession("s2", true);
                        handler.afterConnectionEstablished(s1);
                        handler.afterConnectionEstablished(s2);

                        handler.broadcast("{\"type\":\"campaign_completed\"}");

                        verify(s1).sendMessage(new TextMessage("{\"type\":\"campaign_completed\"}"));
                        verify(s2).sendMessage(new TextMessage("{\"type\":\"campaign_completed\"}"));
                }

                @Test
                void broadcast_closedSession_skipped() throws Exception {
                        WebSocketSession open = mockSession("open", true);
                        WebSocketSession closed = mockSession("closed", false);
                        handler.afterConnectionEstablished(open);
                        handler.afterConnectionEstablished(closed);

                        handler.broadcast("{\"type\":\"campaign_completed\"}");

                        verify(open).sendMessage(new TextMessage("{\"type\":\"campaign_completed\"}"));
                        verify(closed, never()).sendMessage(new TextMessage("{\"type\":\"campaign_completed\"}"));
                }

                @Test
                void broadcast_ioException_removesFailedSession() throws Exception {
                        WebSocketSession failing = mockSession("fail", true);
                        doThrow(new IOException("broken pipe")).when(failing).sendMessage(new TextMessage("{\"x\":1}"));
                        handler.afterConnectionEstablished(failing);

                        handler.broadcast("{\"x\":1}");

                        handler.broadcast("{\"x\":2}");
                        verify(failing, never()).sendMessage(new TextMessage("{\"x\":2}"));
                }
        }
}
