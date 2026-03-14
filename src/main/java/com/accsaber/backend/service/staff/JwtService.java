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
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class JwtService {

    @Value("${accsaber.jwt.secret}")
    private String secret;

    @Value("${accsaber.jwt.access-token-ttl}")
    private long accessTokenTtl;

    public String generateAccessToken(StaffUser staffUser) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(staffUser.getId().toString())
                .claim("username", staffUser.getUsername())
                .claim("role", staffUser.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenTtl)))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    public UUID extractStaffId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    public StaffRole extractRole(String token) {
        String roleName = parseClaims(token).get("role", String.class);
        return StaffRole.valueOf(roleName);
    }

    public void validateToken(String token) {
        parseClaims(token);
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new JwtException("Invalid JWT token: " + e.getMessage(), e);
        }
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
