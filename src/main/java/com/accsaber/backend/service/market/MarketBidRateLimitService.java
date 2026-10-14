package com.accsaber.backend.service.market;

import java.time.Duration;

import org.springframework.stereotype.Service;

import com.accsaber.backend.service.infra.UserRateLimiter;

@Service
public class MarketBidRateLimitService extends UserRateLimiter {

    public MarketBidRateLimitService() {
        super(10, Duration.ofSeconds(10));
    }
}
