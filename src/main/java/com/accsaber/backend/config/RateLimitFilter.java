package com.accsaber.backend.config;

import java.io.IOException;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX = "rate_limit:";

    private final StringRedisTemplate redisTemplate;
    private final long capacity;
    private final Duration window;

    public RateLimitFilter(StringRedisTemplate redisTemplate,
            @Value("${accsaber.rate-limit.capacity:100}") long capacity,
            @Value("${accsaber.rate-limit.window-seconds:60}") long windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.capacity = capacity;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String key = KEY_PREFIX + request.getRemoteAddr();

        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, window);
        }

        long remaining = Math.max(0, capacity - (count != null ? count : 0));
        response.setHeader("X-Rate-Limit-Remaining", String.valueOf(remaining));

        if (count != null && count > capacity) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded. Try again later.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/v1/");
    }
}
