package com.accsaber.backend.websocket;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import com.accsaber.backend.config.PlatformProperties;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberWebSocketMessage;
import com.accsaber.backend.service.score.ScoreIngestionService;
import com.accsaber.backend.util.ScoreSaberWebSocketMessageNormalizer;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WebSocketConnectionManager implements SmartLifecycle {

    private final PlatformProperties properties;
    private final ScoreIngestionService scoreIngestionService;
    private final IngestionLeaderLock leaderLock;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService gapFillExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<ScoreFeed> feeds;

    public WebSocketConnectionManager(PlatformProperties properties,
            ScoreIngestionService scoreIngestionService,
            IngestionLeaderLock leaderLock) {
        this.properties = properties;
        this.scoreIngestionService = scoreIngestionService;
        this.leaderLock = leaderLock;
        this.feeds = List.of(
                new ScoreFeed("beatleader", "BeatLeader", properties.getBeatleader(), this::handleBeatLeader),
                new ScoreFeed("scoresaber", "ScoreSaber", properties.getScoresaber(), this::handleScoreSaber));
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            long interval = properties.getWsReconnectIntervalMs();
            long startDelay = Math.max(0, properties.getWsStartDelaySeconds()) * 1000L;
            reconnectScheduler.schedule(this::connectAll, startDelay, TimeUnit.MILLISECONDS);
            reconnectScheduler.scheduleWithFixedDelay(this::monitorConnections, startDelay + interval, interval,
                    TimeUnit.MILLISECONDS);
            log.info("WebSocket connection manager started, connecting in {}ms", startDelay);
        }
    }

    private void connectAll() {
        if (!running.get()) {
            return;
        }
        if (!leaderLock.acquire()) {
            log.info("Another instance owns score ingestion - standing by until it releases");
            return;
        }
        feeds.forEach(ScoreFeed::connect);
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            feeds.forEach(ScoreFeed::close);
            reconnectScheduler.shutdown();
            gapFillExecutor.shutdown();
            leaderLock.release();
            log.info("WebSocket connection manager stopped");
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        feeds.forEach(feed -> status.put(feed.platform, feed.status()));
        return status;
    }

    public void reconnect(String platform) {
        for (ScoreFeed feed : feeds) {
            if (feed.platform.equalsIgnoreCase(platform)) {
                feed.close();
                feed.reconnectMs.set(properties.getWsReconnectIntervalMs());
                feed.connect();
            }
        }
    }

    private void monitorConnections() {
        if (!running.get())
            return;
        if (!leaderLock.acquire()) {
            if (feeds.stream().anyMatch(feed -> feed.client != null)) {
                log.warn("Lost score ingestion leadership - closing both sockets");
                feeds.forEach(feed -> {
                    feed.close();
                    feed.client = null;
                });
            }
            return;
        }
        feeds.forEach(ScoreFeed::monitor);
    }

    private void handleBeatLeader(String message) throws Exception {
        scoreIngestionService.handleBeatLeaderScore(objectMapper.readValue(message, BeatLeaderScoreResponse.class));
    }

    private void handleScoreSaber(String message) throws Exception {
        if (message == null || !message.startsWith("{")) {
            return;
        }
        ScoreSaberWebSocketMessage wsMessage = ScoreSaberWebSocketMessageNormalizer.normalize(message, objectMapper);
        var scoreData = wsMessage.getScore();
        var leaderboard = wsMessage.getLeaderboard();
        if (scoreData == null || scoreData.getPlayer() == null || scoreData.getPlayer().getId() == null
                || leaderboard == null || leaderboard.getId() == null) {
            return;
        }
        Long userId = Long.parseLong(scoreData.getPlayer().getId());
        String ssLeaderboardId = String.valueOf(leaderboard.getId());
        scoreIngestionService.handleScoreSaberScore(scoreData, wsMessage.getScoreStats(), userId, ssLeaderboardId);
    }

    @FunctionalInterface
    private interface MessageHandler {
        void handle(String message) throws Exception;
    }

    private final class ScoreFeed {

        private final String platform;
        private final String label;
        private final PlatformProperties.PlatformConfig config;
        private final MessageHandler handler;
        private final AtomicInteger reconnectMs = new AtomicInteger(properties.getWsReconnectIntervalMs());
        private volatile Client client;
        private volatile Instant lastDisconnectedAt;
        private volatile Instant lastMessageReceivedAt;

        private ScoreFeed(String platform, String label, PlatformProperties.PlatformConfig config,
                MessageHandler handler) {
            this.platform = platform;
            this.label = label;
            this.config = config;
            this.handler = handler;
        }

        private boolean watchesStaleness() {
            return "scoresaber".equals(platform);
        }

        private Map<String, Object> status() {
            String disconnected = lastDisconnectedAt != null ? lastDisconnectedAt.toString() : "never";
            if (!watchesStaleness()) {
                return Map.of("connected", isConnected(), "lastDisconnectedAt", disconnected);
            }
            return Map.of("connected", isConnected(), "lastDisconnectedAt", disconnected,
                    "lastMessageReceivedAt", lastMessageReceivedAt != null ? lastMessageReceivedAt.toString() : "never");
        }

        private boolean isConnected() {
            Client current = client;
            return current != null && current.isOpen();
        }

        private void connect() {
            String url = config.getWebsocketUrl();
            if (url == null || url.isBlank()) {
                log.warn("{} WebSocket URL not configured - skipping", label);
                return;
            }
            try {
                log.info("Connecting {} WebSocket to {}", label, url);
                Client next = new Client(new URI(url));
                client = next;
                next.connect();
            } catch (Exception e) {
                log.error("Failed to connect {} WebSocket to {}: {}", label, url, e.getMessage());
            }
        }

        private void close() {
            Client current = client;
            if (current != null) {
                current.intentionalClose = true;
                current.close();
            }
        }

        private void monitor() {
            int maxMs = properties.getWsMaxReconnectIntervalMs();
            int baseMs = properties.getWsReconnectIntervalMs();
            Client current = client;
            boolean stale = watchesStaleness() && isConnected() && isStale();
            if (stale) {
                log.warn("{} WebSocket connected but stale (no messages received recently) - forcing reconnect",
                        label);
                close();
                current.intentionalClose = false;
            }
            if ((!isConnected() || stale) && (current == null || !current.intentionalClose)) {
                if (reconnectMs.get() <= 0) {
                    log.info("{} WebSocket disconnected - reconnecting", label);
                    connect();
                    reconnectMs.set(Math.min(baseMs * 2, maxMs));
                } else {
                    reconnectMs.addAndGet(-baseMs);
                }
            } else if (isConnected()) {
                reconnectMs.set(baseMs);
            }
        }

        private boolean isStale() {
            Instant lastMsg = lastMessageReceivedAt;
            return lastMsg != null
                    && Instant.now().toEpochMilli() - lastMsg.toEpochMilli() > properties.getSsStaleTimeoutMs();
        }

        private void gapFill(Instant since) {
            log.info("Reconnected - triggering gap fill from {}", since);
            gapFillExecutor.execute(() -> {
                try {
                    scoreIngestionService.gapFill(platform, since);
                } catch (Exception e) {
                    log.error("Gap fill failed: {}", e.getMessage());
                }
            });
        }

        private final class Client extends WebSocketClient {

            private volatile boolean intentionalClose;
            private volatile boolean opened;

            private Client(URI serverUri) {
                super(serverUri);
                setConnectionLostTimeout(30);
            }

            @Override
            public void onOpen(ServerHandshake handshake) {
                log.info("{} WebSocket connected", label);
                opened = true;
                Instant since = lastDisconnectedAt;
                if (since != null) {
                    gapFill(since);
                }
            }

            @Override
            public void onMessage(String message) {
                lastMessageReceivedAt = Instant.now();
                try {
                    handler.handle(message);
                } catch (Exception e) {
                    log.error("Error processing {} WebSocket message: {}", label, e.getMessage());
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                log.warn("{} WebSocket closed (code={}, reason={}, remote={})", label, code, reason, remote);
                if (opened) {
                    lastDisconnectedAt = Instant.now();
                }
            }

            @Override
            public void onError(Exception ex) {
                log.error("{} WebSocket error: {}", label, ex.getMessage());
            }
        }
    }
}
