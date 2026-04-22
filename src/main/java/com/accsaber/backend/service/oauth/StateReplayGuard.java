package com.accsaber.backend.service.oauth;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.accsaber.backend.exception.UnauthorizedException;

@Component
public class StateReplayGuard {

    private final ConcurrentHashMap<String, Instant> seen = new ConcurrentHashMap<>();

    public void consume(String jti, Instant expiresAt) {
        Instant existing = seen.putIfAbsent(jti, expiresAt);
        if (existing != null) {
            throw new UnauthorizedException("OAuth state has already been used");
        }
    }

    @Scheduled(fixedDelay = 60_000L)
    public void purgeExpired() {
        Instant now = Instant.now();
        seen.entrySet().removeIf(e -> e.getValue().isBefore(now));
    }
}
