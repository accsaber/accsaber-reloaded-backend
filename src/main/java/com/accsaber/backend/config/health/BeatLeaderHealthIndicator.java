package com.accsaber.backend.config.health;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component
public class BeatLeaderHealthIndicator extends AbstractHealthIndicator {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private final WebClient webClient;

    public BeatLeaderHealthIndicator(@Qualifier("beatLeaderWebClient") WebClient webClient) {
        super("BeatLeader health check failed");
        this.webClient = webClient;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        long start = System.currentTimeMillis();
        try {
            webClient.get()
                    .uri("/")
                    .retrieve()
                    .toBodilessEntity()
                    .block(TIMEOUT);

            long responseTime = System.currentTimeMillis() - start;
            builder.up().withDetail("responseTime", responseTime + "ms");
        } catch (Exception ex) {
            long responseTime = System.currentTimeMillis() - start;
            builder.down(ex)
                    .withDetail("responseTime", responseTime + "ms");
        }
    }
}
