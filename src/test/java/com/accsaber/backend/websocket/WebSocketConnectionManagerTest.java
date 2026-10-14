package com.accsaber.backend.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.accsaber.backend.config.PlatformProperties;
import com.accsaber.backend.service.score.ScoreIngestionService;

class WebSocketConnectionManagerTest {

    private final ScoreIngestionService scoreIngestionService = mock(ScoreIngestionService.class);
    private final CountDownLatch started = new CountDownLatch(1);
    private WebSocketServer server;
    private WebSocketConnectionManager manager;

    @BeforeEach
    void setUp() throws InterruptedException {
        server = new WebSocketServer(new InetSocketAddress("localhost", 0)) {
            @Override
            public void onOpen(WebSocket conn, ClientHandshake handshake) {
            }

            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            }

            @Override
            public void onMessage(WebSocket conn, String message) {
            }

            @Override
            public void onError(WebSocket conn, Exception ex) {
            }

            @Override
            public void onStart() {
                started.countDown();
            }
        };
        server.start();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        PlatformProperties properties = new PlatformProperties();
        properties.getBeatleader().setWebsocketUrl("ws://localhost:" + server.getPort());
        manager = new WebSocketConnectionManager(properties, scoreIngestionService, mock(IngestionLeaderLock.class));
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        server.stop(1000);
    }

    @Test
    void reconnectAfterDropGapFillsFromTheDisconnectTime() throws InterruptedException {
        manager.reconnect("beatleader");
        awaitTrue(() -> connected());
        verify(scoreIngestionService, after(200).never()).gapFill(anyString(), any());
        assertThat(beatLeaderStatus().get("lastDisconnectedAt")).isEqualTo("never");

        Instant beforeDrop = Instant.now();
        server.getConnections().forEach(WebSocket::close);
        awaitTrue(() -> !connected() && !"never".equals(beatLeaderStatus().get("lastDisconnectedAt")));
        Instant disconnectedAt = Instant.parse((String) beatLeaderStatus().get("lastDisconnectedAt"));
        assertThat(disconnectedAt).isAfterOrEqualTo(beforeDrop);

        manager.reconnect("beatleader");

        verify(scoreIngestionService, timeout(5000)).gapFill(eq("beatleader"), eq(disconnectedAt));
        awaitTrue(() -> connected());
        assertThat(beatLeaderStatus().get("lastDisconnectedAt")).isEqualTo(disconnectedAt.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> beatLeaderStatus() {
        return (Map<String, Object>) manager.getStatus().get("beatleader");
    }

    private boolean connected() {
        return Boolean.TRUE.equals(beatLeaderStatus().get("connected"));
    }

    private static void awaitTrue(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean()) {
            assertThat(System.currentTimeMillis()).isLessThan(deadline);
            Thread.sleep(20);
        }
    }
}
