package com.accsaber.backend.service.market;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Service
public class MarketBidRateLimitService {

    private static final int MAX_BIDS_PER_WINDOW = 10;

    private final Cache<Long, AtomicInteger> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(10))
            .maximumSize(50_000)
            .build();

    public boolean tryAcquire(Long userId) {
        if (userId == null) {
            return false;
        }
        return cache.get(userId, k -> new AtomicInteger(0)).incrementAndGet() <= MAX_BIDS_PER_WINDOW;
    }
}
