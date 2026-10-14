package com.accsaber.backend.config;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.accsaber.backend.exception.TooManyRequestsException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Map<String, Integer> SENSITIVE_PATH_CAPACITY = Map.of(
            "/v1/staff/auth/login", 10,
            "/v1/staff/auth/refresh", 30,
            "/v1/auth/refresh", 30,
            "/v1/auth/ingame", 20);

    private static final int MAX_TRACKED_KEYS = 100_000;
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9.]+|[0-9a-fA-F:.%]*:[0-9a-fA-F:.%]*");

    private final Cache<String, AtomicInteger> counters = Caffeine.newBuilder()
            .maximumSize(MAX_TRACKED_KEYS)
            .build();
    private final int capacity;
    private final Set<String> trustedIps;
    private final List<IpAddressMatcher> trustedProxies;
    private final HandlerExceptionResolver exceptionResolver;

    public RateLimitFilter(
            @Value("${accsaber.rate-limit.capacity:400}") int capacity,
            @Value("${accsaber.rate-limit.trusted-ips:}") String trustedIpsConfig,
            @Value("${accsaber.rate-limit.trusted-proxies:}") String trustedProxiesConfig,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.capacity = capacity;
        this.exceptionResolver = exceptionResolver;
        this.trustedIps = splitList(trustedIpsConfig).collect(Collectors.toSet());
        this.trustedProxies = splitList(trustedProxiesConfig).map(IpAddressMatcher::new).toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String key = resolveClientIp(request);
        AtomicInteger counter = counters.get(key, k -> new AtomicInteger(0));
        int count = counter.incrementAndGet();

        long remaining = Math.max(0, capacity - count);
        response.setHeader("X-Rate-Limit-Remaining", String.valueOf(remaining));

        if (count > capacity) {
            exceptionResolver.resolveException(request, response, null,
                    new TooManyRequestsException("Rate limit exceeded. Try again later."));
            return;
        }

        Integer sensitiveCap = SENSITIVE_PATH_CAPACITY.get(request.getRequestURI());
        if (sensitiveCap != null) {
            String sensitiveKey = key + "|" + request.getRequestURI();
            AtomicInteger sensitiveCounter = counters.get(sensitiveKey, k -> new AtomicInteger(0));
            if (sensitiveCounter.incrementAndGet() > sensitiveCap) {
                exceptionResolver.resolveException(request, response, null,
                        new TooManyRequestsException("Rate limit exceeded. Try again later."));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/v1/")) {
            return true;
        }
        if (!trustedIps.isEmpty() && trustedIps.contains(resolveClientIp(request))) {
            return true;
        }
        return false;
    }

    @Scheduled(fixedRateString = "${accsaber.rate-limit.window-seconds:60}000")
    public void resetCounters() {
        counters.invalidateAll();
    }

    String resolveClientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String cf = request.getHeader("CF-Connecting-IP");
        if (cf == null || cf.isBlank() || !matchesAny(trustedProxies, remote)) {
            return remote;
        }
        return cf.trim();
    }

    static boolean matchesAny(List<IpAddressMatcher> networks, String remote) {
        if (remote == null || !IP_LITERAL.matcher(remote).matches()) {
            return false;
        }
        for (IpAddressMatcher matcher : networks) {
            if (matcher.matches(remote)) {
                return true;
            }
        }
        return false;
    }

    private static Stream<String> splitList(String config) {
        return Stream.of(config.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty());
    }
}
