package com.accsaber.backend.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.model.entity.user.User;

import jakarta.servlet.FilterChain;

class BannedUserWriteFilterTest {

    private final HandlerExceptionResolver exceptionResolver = mock(HandlerExceptionResolver.class);
    private final BannedUserWriteFilter filter = new BannedUserWriteFilter(exceptionResolver);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User user) {
        PlayerUserDetails principal = new PlayerUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private static User player(boolean banned) {
        return User.builder().id(1L).name("P").active(true).banned(banned).build();
    }

    @Test
    void blocksBannedPlayerWrite() throws Exception {
        authenticateAs(player(true));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/campaigns");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(exceptionResolver).resolveException(eq(request), eq(response), isNull(),
                any(ForbiddenException.class));
    }

    @Test
    void allowsBannedPlayerRead() throws Exception {
        authenticateAs(player(true));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/campaigns");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(exceptionResolver);
    }

    @Test
    void allowsBannedPlayerAuthEndpoint() throws Exception {
        authenticateAs(player(true));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/auth/logout");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(exceptionResolver);
    }

    @Test
    void allowsNonBannedPlayerWrite() throws Exception {
        authenticateAs(player(false));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/campaigns");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(exceptionResolver);
    }

    @Test
    void allowsAnonymousWrite() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/webhooks/kofi");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(exceptionResolver);
    }
}
