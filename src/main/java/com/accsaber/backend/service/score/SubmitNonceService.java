package com.accsaber.backend.service.score;

import java.time.Duration;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Service
public class SubmitNonceService {

    private final Cache<String, Boolean> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(50_000)
            .build();

    public boolean tryConsume(Long userId, String nonce) {
        if (userId == null || nonce == null || nonce.isBlank()) {
            return false;
        }
        String key = userId + ":" + nonce;
        return cache.asMap().putIfAbsent(key, Boolean.TRUE) == null;
    }
}
