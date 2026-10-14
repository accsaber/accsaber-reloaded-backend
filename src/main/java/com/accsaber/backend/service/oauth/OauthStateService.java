package com.accsaber.backend.service.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.accsaber.backend.config.OauthProperties;
import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.service.oauth.DiscordOauthClient.DiscordIdentity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OauthStateService {

    private static final String PURPOSE_STATE = "oauth-state";
    private static final String PURPOSE_PENDING_LINK = "oauth-pending-link";
    private static final String BINDING_CLAIM = "bind";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OauthProperties oauthProperties;
    private final StateReplayGuard replayGuard;

    @Value("${accsaber.jwt.secret}")
    private String secret;

    public String newBrowserBinding() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String createState(StateClaims state) {
        if (state.pendingLinkToken() != null) {
            requireBound(parse(state.pendingLinkToken()), state.binding());
        }
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(state.provider())
                .claim("purpose", PURPOSE_STATE)
                .claim("returnTo", state.returnTo())
                .claim(BINDING_CLAIM, state.binding())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(oauthProperties.getStateTtlSeconds())));
        if (state.linkUserId() != null) {
            builder.claim("linkUserId", state.linkUserId().toString());
        }
        if (state.pendingLinkToken() != null) {
            builder.claim("pendingLinkToken", state.pendingLinkToken());
        }
        return builder.signWith(getSigningKey()).compact();
    }

    public StateClaims parseState(String state, String expectedProvider, String binding) {
        Claims claims = parse(state);
        if (!PURPOSE_STATE.equals(claims.get("purpose", String.class))
                || !expectedProvider.equals(claims.getSubject())) {
            throw new UnauthorizedException("Invalid OAuth state");
        }
        requireBound(claims, binding);
        String jti = claims.getId();
        if (jti == null) {
            throw new UnauthorizedException("OAuth state missing jti");
        }
        replayGuard.consume(jti);

        String linkUserIdStr = claims.get("linkUserId", String.class);
        Long linkUserId = null;
        if (linkUserIdStr != null) {
            try {
                linkUserId = Long.parseLong(linkUserIdStr);
            } catch (NumberFormatException e) {
                throw new UnauthorizedException("Invalid OAuth state");
            }
        }
        return new StateClaims(
                claims.getSubject(),
                claims.get("returnTo", String.class),
                linkUserId,
                claims.get("pendingLinkToken", String.class),
                binding);
    }

    public String createPendingLinkToken(DiscordIdentity identity, StateClaims state) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(identity.getId())
                .claim("purpose", PURPOSE_PENDING_LINK)
                .claim("discordUsername", identity.displayName())
                .claim("discordAvatarUrl", identity.avatarUrl())
                .claim("returnTo", state.returnTo())
                .claim(BINDING_CLAIM, state.binding())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(oauthProperties.getPendingLinkTtlSeconds())))
                .signWith(getSigningKey())
                .compact();
    }

    public PendingLinkClaims parsePendingLinkToken(String token) {
        Claims claims = parse(token);
        if (!PURPOSE_PENDING_LINK.equals(claims.get("purpose", String.class))) {
            throw new UnauthorizedException("Invalid pending-link token");
        }
        return new PendingLinkClaims(
                claims.getSubject(),
                claims.get("discordUsername", String.class),
                claims.get("discordAvatarUrl", String.class),
                claims.get("returnTo", String.class));
    }

    private static void requireBound(Claims claims, String binding) {
        String expected = claims.get(BINDING_CLAIM, String.class);
        if (expected == null || binding == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), binding.getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException("OAuth flow was started in a different browser");
        }
    }

    private Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("Invalid or expired token");
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    public record StateClaims(String provider, String returnTo, Long linkUserId, String pendingLinkToken,
            String binding) {
    }

    public record PendingLinkClaims(String discordId, String discordUsername, String discordAvatarUrl,
            String returnTo) {
    }
}
