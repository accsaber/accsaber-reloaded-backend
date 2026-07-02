package com.accsaber.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.accsaber.backend.websocket.server.CampaignPresenceHandshakeInterceptor;
import com.accsaber.backend.websocket.server.CampaignPresenceWebSocketHandler;
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
        private final CampaignPresenceWebSocketHandler campaignPresenceHandler;
        private final CampaignPresenceHandshakeInterceptor campaignPresenceHandshakeInterceptor;

        @Override
        public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
                registry.addHandler(scoreFeedHandler, "/ws/scores")
                                .setAllowedOriginPatterns("*");
                registry.addHandler(milestoneFeedHandler, "/ws/milestones")
                                .setAllowedOriginPatterns("*");
                registry.addHandler(missionFeedHandler, "/ws/missions")
                                .setAllowedOriginPatterns("*");
                registry.addHandler(campaignPresenceHandler, "/ws/campaigns/presence")
                                .addInterceptors(campaignPresenceHandshakeInterceptor)
                                .setAllowedOriginPatterns("*");
        }
}
