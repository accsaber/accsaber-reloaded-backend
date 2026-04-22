package com.accsaber.backend.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;
import lombok.ToString;

@Data
@Configuration
@ConfigurationProperties(prefix = "accsaber.oauth")
public class OauthProperties {

    private long stateTtlSeconds = 600;
    private long pendingLinkTtlSeconds = 900;
    private List<String> allowedReturnOrigins = new ArrayList<>();

    private ProviderConfig discord = new ProviderConfig();
    private ProviderConfig beatleader = new ProviderConfig();
    private SteamConfig steam = new SteamConfig();

    @Data
    public static class ProviderConfig {
        private String clientId;
        @ToString.Exclude
        private String clientSecret;
        private String authorizeUrl;
        private String tokenUrl;
        private String userInfoUrl;
        private String redirectUri;
        private String scope;
    }

    @Data
    public static class SteamConfig {
        private String realm;
        private String returnTo;
    }
}
