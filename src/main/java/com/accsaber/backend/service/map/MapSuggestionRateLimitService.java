package com.accsaber.backend.service.map;

import java.time.Duration;

import org.springframework.stereotype.Service;

import com.accsaber.backend.service.infra.UserRateLimiter;

@Service
public class MapSuggestionRateLimitService extends UserRateLimiter {

    public MapSuggestionRateLimitService() {
        super(10, Duration.ofMinutes(10));
    }
}
