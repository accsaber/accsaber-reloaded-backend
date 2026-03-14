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

class ScoreFeedWebSocketHandlerTest {

        private ScoreFeedWebSocketHandler handler;

        @BeforeEach
        void setUp() {
                handler = new ScoreFeedWebSocketHandler();
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
                        handler.broadcast("{\"test\":true}");
                        assertThat(session.getId()).isEqualTo("s1");
                }

                @Test
                void afterConnectionClosed_removesSession() throws Exception {
                        WebSocketSession session = mockSession("s1", true);
                        handler.afterConnectionEstablished(session);

                        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

                        handler.broadcast("{\"test\":true}");
                        verify(session, never()).sendMessage(new TextMessage("{\"test\":true}"));
                }

                @Test
                void handleTransportError_removesSession() throws Exception {
                        WebSocketSession session = mockSession("s1", true);
                        handler.afterConnectionEstablished(session);

                        handler.handleTransportError(session, new RuntimeException("connection lost"));

                        handler.broadcast("{\"test\":true}");
                        verify(session, never()).sendMessage(new TextMessage("{\"test\":true}"));
                }
        }

        @Nested
        class Broadcast {

                @Test
                void broadcast_noSessions_doesNotThrow() {
                        handler.broadcast("{\"score\":100}");
                }

                @Test
                void broadcast_multipleSessions_sendsToAll() throws Exception {
                        WebSocketSession s1 = mockSession("s1", true);
                        WebSocketSession s2 = mockSession("s2", true);
                        handler.afterConnectionEstablished(s1);
                        handler.afterConnectionEstablished(s2);

                        handler.broadcast("{\"score\":100}");

                        verify(s1).sendMessage(new TextMessage("{\"score\":100}"));
                        verify(s2).sendMessage(new TextMessage("{\"score\":100}"));
                }

                @Test
                void broadcast_closedSession_skipped() throws Exception {
                        WebSocketSession open = mockSession("open", true);
                        WebSocketSession closed = mockSession("closed", false);
                        handler.afterConnectionEstablished(open);
                        handler.afterConnectionEstablished(closed);

                        handler.broadcast("{\"score\":100}");

                        verify(open).sendMessage(new TextMessage("{\"score\":100}"));
                        verify(closed, never()).sendMessage(new TextMessage("{\"score\":100}"));
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
