package com.accsaber.backend.security;

import java.io.IOException;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.accsaber.backend.exception.ForbiddenException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class BannedUserWriteFilter extends OncePerRequestFilter {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final HandlerExceptionResolver exceptionResolver;

    public BannedUserWriteFilter(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isBannedPlayerWrite(request)) {
            exceptionResolver.resolveException(request, response, null,
                    new ForbiddenException("Your account is banned and cannot create or modify content"));
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isBannedPlayerWrite(HttpServletRequest request) {
        if (!WRITE_METHODS.contains(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (path != null && path.startsWith("/v1/auth/")) {
            return false;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getPrincipal() instanceof PlayerUserDetails player
                && player.getUser().isBanned();
    }
}
