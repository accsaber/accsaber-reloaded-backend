package com.accsaber.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.staff.JwtService;

import io.jsonwebtoken.JwtException;

@ExtendWith(MockitoExtension.class)
class PlayerTokenResolverTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DuplicateUserService duplicateUserService;

    @InjectMocks
    private PlayerTokenResolver resolver;

    private void playerToken(long tokenUserId, long primaryUserId) {
        when(jwtService.extractTokenType("tok")).thenReturn(JwtService.TYPE_PLAYER);
        when(jwtService.extractPlayerId("tok")).thenReturn(tokenUserId);
        when(duplicateUserService.resolvePrimaryUserId(tokenUserId)).thenReturn(primaryUserId);
    }

    @Test
    void validPlayerTokenResolvesToThePrimaryAccount() {
        playerToken(4L, 5L);
        User primary = User.builder().id(5L).active(true).banned(false).build();
        when(userRepository.findByIdAndActiveTrue(5L)).thenReturn(Optional.of(primary));

        PlayerTokenResolver.Resolution resolution = resolver.resolve("tok");

        assertThat(resolution.accepted()).isTrue();
        assertThat(resolution.user()).isSameAs(primary);
    }

    @Test
    void missingTokenIsABadRequest() {
        assertThat(resolver.resolve(" ").rejection()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resolver.resolve(null).rejection()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void staffTokenIsUnauthorized() {
        when(jwtService.extractTokenType("tok")).thenReturn(JwtService.TYPE_STAFF);

        assertThat(resolver.resolve("tok").rejection()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void brokenTokenIsUnauthorized() {
        doThrow(new JwtException("expired")).when(jwtService).validateToken("tok");

        PlayerTokenResolver.Resolution resolution = resolver.resolve("tok");

        assertThat(resolution.accepted()).isFalse();
        assertThat(resolution.rejection()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void bannedPlayerIsForbidden() {
        playerToken(5L, 5L);
        when(userRepository.findByIdAndActiveTrue(5L))
                .thenReturn(Optional.of(User.builder().id(5L).active(true).banned(true).build()));

        assertThat(resolver.resolve("tok").rejection()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void inactivePlayerIsForbidden() {
        playerToken(5L, 5L);
        when(userRepository.findByIdAndActiveTrue(5L)).thenReturn(Optional.empty());

        assertThat(resolver.resolve("tok").rejection()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
