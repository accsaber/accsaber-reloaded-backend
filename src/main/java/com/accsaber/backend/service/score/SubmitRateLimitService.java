package com.accsaber.backend.service.score;

import java.time.Duration;

import org.springframework.stereotype.Service;

import com.accsaber.backend.service.infra.UserRateLimiter;

@Service
public class SubmitRateLimitService extends UserRateLimiter {

    public SubmitRateLimitService() {
        super(1, Duration.ofMinutes(1));
    }
}
