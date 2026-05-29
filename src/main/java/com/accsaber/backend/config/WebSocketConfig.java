package com.accsaber.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.accsaber.backend.websocket.server.MilestoneFeedWebSocketHandler;
import com.accsaber.backend.websocket.server.MissionFeedWebSocketHandler;
import com.accsaber.backend.websocket.server.ScoreFeedWebSocketHandler;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ScoreFeedWebSocketHandler scoreFeedHandler;
    private final MilestoneFeedWebSocketHandler milestoneFeedHandler;
    private final MissionFeedWebSocketHandler missionFeedHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(scoreFeedHandler, "/ws/scores")
                .setAllowedOrigins("*");
        registry.addHandler(milestoneFeedHandler, "/ws/milestones")
                .setAllowedOrigins("*");
        registry.addHandler(missionFeedHandler, "/ws/missions")
                .setAllowedOrigins("*");
    }
}
