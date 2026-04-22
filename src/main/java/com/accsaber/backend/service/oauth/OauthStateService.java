package com.accsaber.backend.service.oauth;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.accsaber.backend.config.OauthProperties;
import com.accsaber.backend.exception.UnauthorizedException;

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

    private final OauthProperties oauthProperties;
    private final StateReplayGuard replayGuard;

    @Value("${accsaber.jwt.secret}")
    private String secret;

    public String createState(String provider, String returnTo, Long linkUserId, String pendingLinkToken) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(provider)
                .claim("purpose", PURPOSE_STATE)
                .claim("returnTo", returnTo)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(oauthProperties.getStateTtlSeconds())));
        if (linkUserId != null) {
            builder.claim("linkUserId", linkUserId.toString());
        }
        if (pendingLinkToken != null) {
            builder.claim("pendingLinkToken", pendingLinkToken);
        }
        return builder.signWith(getSigningKey()).compact();
    }

    public StateClaims parseState(String state, String expectedProvider) {
        Claims claims = parse(state);
        if (!PURPOSE_STATE.equals(claims.get("purpose", String.class))
                || !expectedProvider.equals(claims.getSubject())) {
            throw new UnauthorizedException("Invalid OAuth state");
        }
        String jti = claims.getId();
        if (jti == null) {
            throw new UnauthorizedException("OAuth state missing jti");
        }
        replayGuard.consume(jti, claims.getExpiration().toInstant());

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
                claims.get("pendingLinkToken", String.class));
    }

    public String createPendingLinkToken(String discordId, String discordUsername, String discordAvatarUrl,
            String returnTo) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(discordId)
                .claim("purpose", PURPOSE_PENDING_LINK)
                .claim("discordUsername", discordUsername)
                .claim("discordAvatarUrl", discordAvatarUrl)
                .claim("returnTo", returnTo)
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

    public record StateClaims(String provider, String returnTo, Long linkUserId, String pendingLinkToken) {
    }

    public record PendingLinkClaims(String discordId, String discordUsername, String discordAvatarUrl,
            String returnTo) {
    }
}
