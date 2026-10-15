package com.accsaber.backend.service.staff;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.model.entity.staff.StaffUser;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class JwtService {

    public static final String TYPE_STAFF = "staff";
    public static final String TYPE_PLAYER = "player";

    public static final String SCOPE_GAME = "game";
    public static final String SCOPE_WEB = "web";

    private final SecretKey signingKey;
    private final JwtParser parser;
    private final long accessTokenTtl;
    private final long playerAccessTokenTtl;

    public JwtService(@Value("${accsaber.jwt.secret}") String secret,
            @Value("${accsaber.jwt.access-token-ttl}") long accessTokenTtl,
            @Value("${accsaber.jwt.player-access-token-ttl}") long playerAccessTokenTtl) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.parser = Jwts.parser().verifyWith(signingKey).build();
        this.accessTokenTtl = accessTokenTtl;
        this.playerAccessTokenTtl = playerAccessTokenTtl;
    }

    public String generateAccessToken(StaffUser staffUser) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(staffUser.getId().toString())
                .claim("typ", TYPE_STAFF)
                .claim("username", staffUser.getUsername())
                .claim("role", staffUser.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenTtl)))
                .signWith(signingKey)
                .compact();
    }

    public String generatePlayerAccessToken(Long userId, String scope) {
        return generatePlayerAccessToken(userId, null, null, scope);
    }

    public String generatePlayerAccessToken(Long userId, UUID staffId, StaffRole staffRole, String scope) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if ((staffId == null) != (staffRole == null)) {
            throw new IllegalArgumentException("staffId and staffRole must both be provided or both be null");
        }
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim("typ", TYPE_PLAYER)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(playerAccessTokenTtl)));
        if (staffId != null) {
            builder.claim("staffId", staffId.toString());
            builder.claim("role", staffRole.name());
        }
        if (scope != null) {
            builder.claim("scope", scope);
        }
        return builder.signWith(signingKey).compact();
    }

    public long getPlayerAccessTokenTtl() {
        return playerAccessTokenTtl;
    }

    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    public record VerifiedToken(String type, String subject, String scope) {

        public Long playerId() {
            return Long.parseLong(subject);
        }

        public UUID staffId() {
            return UUID.fromString(subject);
        }
    }

    public VerifiedToken verify(String token) {
        Claims claims = parseClaims(token);
        return new VerifiedToken(claims.get("typ", String.class), claims.getSubject(),
                claims.get("scope", String.class));
    }

    private Claims parseClaims(String token) {
        try {
            return parser.parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new JwtException(e.getMessage(), e);
        }
    }
}
