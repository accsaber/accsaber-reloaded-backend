package com.accsaber.backend.websocket;

import java.net.URI;
import java.time.Instant;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderScoreResponse;
import com.accsaber.backend.service.score.ScoreIngestionService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BeatLeaderWebSocketListener extends WebSocketClient {

    private final ScoreIngestionService scoreIngestionService;
    private final ObjectMapper objectMapper;
    private volatile Instant lastDisconnectedAt;
    private volatile boolean intentionalClose = false;

    public BeatLeaderWebSocketListener(URI serverUri,
            ScoreIngestionService scoreIngestionService,
            ObjectMapper objectMapper) {
        super(serverUri);
        this.scoreIngestionService = scoreIngestionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        log.info("BeatLeader WebSocket connected");
        if (lastDisconnectedAt != null) {
            log.info("Reconnected - triggering gap fill from {}", lastDisconnectedAt);
            try {
                scoreIngestionService.gapFill("beatleader", lastDisconnectedAt);
            } catch (Exception e) {
                log.error("Gap fill failed: {}", e.getMessage());
            }
            lastDisconnectedAt = null;
        }
    }

    @Override
    public void onMessage(String message) {
        try {
            BeatLeaderScoreResponse score = objectMapper.readValue(message, BeatLeaderScoreResponse.class);
            scoreIngestionService.handleBeatLeaderScore(score);
        } catch (Exception e) {
            log.error("Error processing BL WebSocket message: {}", e.getMessage());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.warn("BeatLeader WebSocket closed (code={}, reason={}, remote={})", code, reason, remote);
        lastDisconnectedAt = Instant.now();
    }

    @Override
    public void onError(Exception ex) {
        log.error("BeatLeader WebSocket error: {}", ex.getMessage());
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
