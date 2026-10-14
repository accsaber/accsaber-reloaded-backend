package com.accsaber.backend.config.health;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class HealthIndicatorConfig {

    @Bean
    public HttpPingHealthIndicator beatLeaderHealthIndicator(@Qualifier("beatLeaderWebClient") WebClient webClient) {
        return new HttpPingHealthIndicator("BeatLeader", webClient, "/");
    }

    @Bean
    public HttpPingHealthIndicator beatSaverHealthIndicator(@Qualifier("beatSaverWebClient") WebClient webClient) {
        return new HttpPingHealthIndicator("BeatSaver", webClient, "/");
    }

    @Bean
    public HttpPingHealthIndicator scoreSaberHealthIndicator(@Qualifier("scoreSaberWebClient") WebClient webClient) {
        return new HttpPingHealthIndicator("ScoreSaber", webClient, "/v2/health");
    }
}
