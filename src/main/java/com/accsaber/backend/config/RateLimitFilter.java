package com.accsaber.backend.config;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.accsaber.backend.exception.TooManyRequestsException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    private final int capacity;
    private final Set<String> trustedIps;
    private final HandlerExceptionResolver exceptionResolver;

    public RateLimitFilter(
            @Value("${accsaber.rate-limit.capacity:400}") int capacity,
            @Value("${accsaber.rate-limit.trusted-ips:}") String trustedIpsConfig,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.capacity = capacity;
        this.exceptionResolver = exceptionResolver;
        this.trustedIps = trustedIpsConfig.isBlank()
                ? Set.of()
                : Stream.of(trustedIpsConfig.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String key = request.getRemoteAddr();
        AtomicInteger counter = counters.computeIfAbsent(key, k -> new AtomicInteger(0));
        int count = counter.incrementAndGet();

        long remaining = Math.max(0, capacity - count);
        response.setHeader("X-Rate-Limit-Remaining", String.valueOf(remaining));

        if (count > capacity) {
            exceptionResolver.resolveException(request, response, null,
                    new TooManyRequestsException("Rate limit exceeded. Try again later."));
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/v1/")) {
            return true;
        }
        if (!trustedIps.isEmpty() && trustedIps.contains(request.getRemoteAddr())) {
            return true;
        }
        return false;
    }

    @Scheduled(fixedRateString = "${accsaber.rate-limit.window-seconds:60}000")
    public void resetCounters() {
        counters.clear();
    }
}