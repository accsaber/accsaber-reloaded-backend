package com.accsaber.backend.service.infra;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public class UserRateLimiter {

    private final int maxPerWindow;
    private final Cache<Long, AtomicInteger> cache;

    public UserRateLimiter(int maxPerWindow, Duration window) {
        this.maxPerWindow = maxPerWindow;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(window)
                .maximumSize(50_000)
                .build();
    }

    public boolean tryAcquire(Long userId) {
        if (userId == null) {
            return false;
        }
        return cache.get(userId, k -> new AtomicInteger(0)).incrementAndGet() <= maxPerWindow;
    }
}
