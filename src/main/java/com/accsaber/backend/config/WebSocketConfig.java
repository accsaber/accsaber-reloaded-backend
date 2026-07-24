package com.accsaber.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.accsaber.backend.websocket.server.CampaignPresenceHandshakeInterceptor;
import com.accsaber.backend.websocket.server.CampaignPresenceWebSocketHandler;
import com.accsaber.backend.websocket.server.CampaignProgressWebSocketHandler;
import com.accsaber.backend.websocket.server.MarketFeedWebSocketHandler;
import com.accsaber.backend.websocket.server.MilestoneFeedWebSocketHandler;
import com.accsaber.backend.websocket.server.MissionFeedWebSocketHandler;
import com.accsaber.backend.websocket.server.NotificationHandshakeInterceptor;
import com.accsaber.backend.websocket.server.NotificationWebSocketHandler;
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
        private final CampaignProgressWebSocketHandler campaignProgressHandler;
        private final MarketFeedWebSocketHandler marketFeedHandler;
        private final NotificationWebSocketHandler notificationHandler;
        private final NotificationHandshakeInterceptor notificationHandshakeInterceptor;

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
                registry.addHandler(campaignProgressHandler, "/ws/campaigns/progress")
                                .setAllowedOriginPatterns("*");
                registry.addHandler(marketFeedHandler, "/ws/market")
                                .setAllowedOriginPatterns("*");
                registry.addHandler(notificationHandler, "/ws/notifications")
                                .addInterceptors(notificationHandshakeInterceptor)
                                .setAllowedOriginPatterns("*");
        }
}
