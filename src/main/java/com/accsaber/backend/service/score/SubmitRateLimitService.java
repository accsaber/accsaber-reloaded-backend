package com.accsaber.backend.service.score;

import java.time.Duration;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Service
public class SubmitRateLimitService {

    private final Cache<Long, Boolean> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(50_000)
            .build();

    public boolean tryAcquire(Long userId) {
        if (userId == null) {
            return false;
        }
        return cache.asMap().putIfAbsent(userId, Boolean.TRUE) == null;
    }
}
