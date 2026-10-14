package com.accsaber.backend.service.oauth;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.accsaber.backend.config.OauthProperties;
import com.accsaber.backend.exception.UnauthorizedException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Component
public class StateReplayGuard {

    private final Cache<String, Boolean> seen;

    public StateReplayGuard(OauthProperties oauthProperties) {
        this.seen = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(oauthProperties.getStateTtlSeconds()))
                .build();
    }

    public void consume(String jti) {
        if (seen.asMap().putIfAbsent(jti, Boolean.TRUE) != null) {
            throw new UnauthorizedException("OAuth state has already been used");
        }
    }
}
