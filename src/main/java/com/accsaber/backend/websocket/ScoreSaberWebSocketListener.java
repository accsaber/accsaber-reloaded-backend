package com.accsaber.backend.websocket;

import java.net.URI;
import java.time.Instant;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberWebSocketMessage;
import com.accsaber.backend.service.score.ScoreIngestionService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ScoreSaberWebSocketListener extends WebSocketClient {

    private final ScoreIngestionService scoreIngestionService;
    private final ObjectMapper objectMapper;
    private volatile Instant lastDisconnectedAt;
    private volatile boolean intentionalClose = false;

    public ScoreSaberWebSocketListener(URI serverUri,
            ScoreIngestionService scoreIngestionService,
            ObjectMapper objectMapper) {
        super(serverUri);
        this.scoreIngestionService = scoreIngestionService;
        this.objectMapper = objectMapper;
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
        if (message == null || !message.startsWith("{")) {
            return;
        }
        try {
            ScoreSaberWebSocketMessage wsMessage = objectMapper.readValue(message, ScoreSaberWebSocketMessage.class);
            if (!"score".equals(wsMessage.getCommandName()) || wsMessage.getCommandData() == null) {
                return;
            }
            var scoreData = wsMessage.getCommandData().getScore();
            var leaderboard = wsMessage.getCommandData().getLeaderboard();
            if (scoreData == null || scoreData.getLeaderboardPlayerInfo() == null || leaderboard == null) {
                return;
            }
            Long steamId = Long.parseLong(scoreData.getLeaderboardPlayerInfo().getId());
            String ssLeaderboardId = String.valueOf(leaderboard.getId());
            scoreIngestionService.handleScoreSaberScore(scoreData, steamId, ssLeaderboardId);
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

    public void setIntentionalClose(boolean intentionalClose) {
        this.intentionalClose = intentionalClose;
    }

    public boolean isIntentionalClose() {
        return intentionalClose;
    }
}
