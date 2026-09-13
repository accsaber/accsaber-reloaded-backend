package com.accsaber.backend.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.staff.JwtService;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PlayerTokenResolver {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final DuplicateUserService duplicateUserService;

    public record Resolution(User user, HttpStatus rejection, String reason) {

        static Resolution rejected(HttpStatus rejection, String reason) {
            return new Resolution(null, rejection, reason);
        }

        public boolean accepted() {
            return user != null;
        }
    }

    public Resolution resolve(String token) {
        if (token == null || token.isBlank()) {
            return Resolution.rejected(HttpStatus.BAD_REQUEST, "missing token");
        }
        Long userId;
        try {
            jwtService.validateToken(token);
            if (!JwtService.TYPE_PLAYER.equals(jwtService.extractTokenType(token))) {
                return Resolution.rejected(HttpStatus.UNAUTHORIZED, "non-player token");
            }
            userId = duplicateUserService.resolvePrimaryUserId(jwtService.extractPlayerId(token));
        } catch (JwtException | IllegalArgumentException e) {
            return Resolution.rejected(HttpStatus.UNAUTHORIZED, "invalid token (" + e.getMessage() + ")");
        }
        return userRepository.findByIdAndActiveTrue(userId)
                .filter(user -> !user.isBanned())
                .map(user -> new Resolution(user, null, null))
                .orElseGet(() -> Resolution.rejected(HttpStatus.FORBIDDEN, "user " + userId + " inactive or banned"));
    }
}
