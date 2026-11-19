package com.accsaber.backend.service.map;

import java.time.Duration;

import org.springframework.stereotype.Service;

import com.accsaber.backend.service.infra.UserRateLimiter;

@Service
public class CriteriaCheckRateLimitService extends UserRateLimiter {

    public CriteriaCheckRateLimitService() {
        super(5, Duration.ofMinutes(10));
    }
}
