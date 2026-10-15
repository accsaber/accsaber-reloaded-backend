package com.accsaber.backend.service.oauth;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.accsaber.backend.config.OauthProperties;
import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.service.oauth.DiscordOauthClient.DiscordIdentity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class OauthStateService {

    private static final String PURPOSE_STATE = "oauth-state";
    private static final String PURPOSE_PENDING_LINK = "oauth-pending-link";
    private static final String BINDING_CLAIM = "bind";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> KNOWN_PROVIDERS = Set.of(
            OauthService.PROVIDER_DISCORD,
            OauthService.PROVIDER_BEATLEADER,
            OauthService.PROVIDER_STEAM);

    private final OauthProperties oauthProperties;
    private final StateReplayGuard replayGuard;
    private final SecretKey signingKey;
    private final JwtParser parser;

    public OauthStateService(OauthProperties oauthProperties, StateReplayGuard replayGuard,
            @Value("${accsaber.jwt.secret}") String secret) {
        this.oauthProperties = oauthProperties;
        this.replayGuard = replayGuard;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.parser = Jwts.parser().verifyWith(signingKey).build();
    }

    public String newBrowserBinding() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public void requireKnownProvider(String provider) {
        if (!KNOWN_PROVIDERS.contains(provider)) {
            throw new ValidationException("Unknown OAuth provider: " + provider);
        }
    }

    public String createState(StateClaims state) {
        requireKnownProvider(state.provider());
        validateReturnTo(state.returnTo());
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
        return builder.signWith(signingKey).compact();
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
                .signWith(signingKey)
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
            return parser.parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("Invalid or expired token");
        }
    }

    private void validateReturnTo(String returnTo) {
        var allowed = oauthProperties.getAllowedReturnOrigins();
        if (allowed == null || allowed.isEmpty()) {
            throw new ValidationException("OAuth allowed-return-origins is not configured");
        }
        String inboundOrigin = originOf(parseUri(returnTo));
        if (inboundOrigin == null) {
            throw new ValidationException("returnTo must be an absolute URL");
        }
        boolean ok = allowed.stream()
                .map(origin -> originOf(parseUri(origin)))
                .anyMatch(inboundOrigin::equalsIgnoreCase);
        if (!ok) {
            throw new ValidationException("returnTo is not in the allowed origin list");
        }
    }

    private URI parseUri(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Invalid URL: " + value);
        }
    }

    private String originOf(URI uri) {
        if (uri.getScheme() == null || uri.getHost() == null) {
            return null;
        }
        int port = uri.getPort();
        return port < 0
                ? uri.getScheme() + "://" + uri.getHost()
                : uri.getScheme() + "://" + uri.getHost() + ":" + port;
    }

    public record StateClaims(String provider, String returnTo, Long linkUserId, String pendingLinkToken,
            String binding) {
    }

    public record PendingLinkClaims(String discordId, String discordUsername, String discordAvatarUrl,
            String returnTo) {
    }
}
