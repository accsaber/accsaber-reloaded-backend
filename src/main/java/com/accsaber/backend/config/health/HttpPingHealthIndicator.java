package com.accsaber.backend.config.health;

import java.time.Duration;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.web.reactive.function.client.WebClient;

public class HttpPingHealthIndicator extends AbstractHealthIndicator {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final WebClient webClient;
    private final String path;

    public HttpPingHealthIndicator(String name, WebClient webClient, String path) {
        super(name + " health check failed");
        this.webClient = webClient;
        this.path = path;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        long start = System.currentTimeMillis();
        try {
            webClient.get()
                    .uri(path)
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
