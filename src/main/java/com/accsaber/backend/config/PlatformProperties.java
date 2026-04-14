package com.accsaber.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "accsaber.platforms")
public class PlatformProperties {

    private PlatformConfig beatleader = new PlatformConfig();
    private PlatformConfig scoresaber = new PlatformConfig();
    private PlatformConfig beatsaver = new PlatformConfig();
    private int ssWaitForBlSeconds = 30;
    private int gapFillWindowSeconds = 60;

    private int wsReconnectIntervalMs = 10000;
    private int wsMaxReconnectIntervalMs = 60000;
    private int ssStaleTimeoutMs = 120000;

    @Data
    public static class PlatformConfig {
        private String baseUrl;
        private String websocketUrl;
        private int timeoutMs = 10000;
        private int maxRetries = 3;
    }
}
