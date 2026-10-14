package com.accsaber.backend.service.chat;

import java.time.Duration;

import org.springframework.stereotype.Service;

import com.accsaber.backend.service.infra.UserRateLimiter;

@Service
public class ChatRateLimitService extends UserRateLimiter {

    public ChatRateLimitService() {
        super(10, Duration.ofSeconds(10));
    }
}
