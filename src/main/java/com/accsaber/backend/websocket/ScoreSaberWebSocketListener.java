package com.accsaber.backend.websocket;

import java.net.URI;
import java.time.Instant;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberWebSocketMessage;
import com.accsaber.backend.service.score.ScoreIngestionService;
import com.accsaber.backend.util.ScoreSaberWebSocketMessageNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ScoreSaberWebSocketListener extends WebSocketClient {

    private final ScoreIngestionService scoreIngestionService;
    private final ObjectMapper objectMapper;
    private volatile Instant lastDisconnectedAt;
    private volatile Instant lastMessageReceivedAt;
    private volatile boolean intentionalClose = false;

    public ScoreSaberWebSocketListener(URI serverUri,
            ScoreIngestionService scoreIngestionService,
            ObjectMapper objectMapper) {
        super(serverUri);
        this.scoreIngestionService = scoreIngestionService;
        this.objectMapper = objectMapper;
        setConnectionLostTimeout(30);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        log.info("ScoreSaber WebSocket connected");
        if (lastDisconnectedAt != null) {
            log.info("Reconnected - triggering gap fill from {}", lastDisconnectedAt);
            try {
                scoreIngestionService.gapFill("scoresaber", lastDisconnectedAt);
            } catch (Exception e) {
                log.error("Gap fill failed: {}", e.getMessage());
            }
            lastDisconnectedAt = null;
        }
    }

    @Override
    public void onMessage(String message) {
        lastMessageReceivedAt = Instant.now();
        if (message == null || !message.startsWith("{")) {
            return;
        }
        try {
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
        } catch (Exception e) {
            log.error("Error processing SS WebSocket message: {}", e.getMessage());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.warn("ScoreSaber WebSocket closed (code={}, reason={}, remote={})", code, reason, remote);
        lastDisconnectedAt = Instant.now();
    }

    @Override
    public void onError(Exception ex) {
        log.error("ScoreSaber WebSocket error: {}", ex.getMessage());
    }

    public Instant getLastDisconnectedAt() {
        return lastDisconnectedAt;
    }

    public Instant getLastMessageReceivedAt() {
        return lastMessageReceivedAt;
    }

    public void setIntentionalClose(boolean intentionalClose) {
        this.intentionalClose = intentionalClose;
    }

    public boolean isIntentionalClose() {
        return intentionalClose;
    }
}
