package com.accsaber.backend.service.oauth;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.model.dto.response.AuthMeResponse;
import com.accsaber.backend.model.dto.response.AuthMeResponse.OauthConnectionSummary;
import com.accsaber.backend.model.dto.response.AuthMeResponse.StaffContext;
import com.accsaber.backend.model.dto.response.PlayerAuthResponse;
import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.model.entity.staff.StaffUserStatus;
import com.accsaber.backend.model.entity.user.OauthConnection;
import com.accsaber.backend.model.entity.user.OauthSession;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.staff.StaffUserRepository;
import com.accsaber.backend.repository.user.OauthConnectionRepository;
import com.accsaber.backend.repository.user.OauthSessionRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.oauth.BeatLeaderOauthClient.BeatLeaderIdentity;
import com.accsaber.backend.service.oauth.DiscordOauthClient.DiscordIdentity;
import com.accsaber.backend.service.oauth.OauthStateService.PendingLinkClaims;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.staff.JwtService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OauthService {

    public static final String PROVIDER_DISCORD = "discord";
    public static final String PROVIDER_BEATLEADER = "beatleader";
    public static final String PROVIDER_STEAM = "steam";

    private static final List<StaffRole> OAUTH_ELIGIBLE_ROLES = List.of(StaffRole.RANKING, StaffRole.RANKING_HEAD);
    private static final Comparator<StaffUser> HIGHEST_ROLE_FIRST = Comparator
            .comparingInt((StaffUser s) -> OAUTH_ELIGIBLE_ROLES.indexOf(s.getRole()))
            .reversed();

    private final OauthConnectionRepository oauthConnectionRepository;
    private final OauthSessionRepository oauthSessionRepository;
    private final UserRepository userRepository;
    private final StaffUserRepository staffUserRepository;
    private final DuplicateUserService duplicateUserService;
    private final DiscordOauthClient discordClient;
    private final BeatLeaderOauthClient beatLeaderClient;
    private final SteamOpenIdClient steamClient;
    private final OauthStateService stateService;
    private final JwtService jwtService;

    @Value("${accsaber.jwt.player-refresh-token-ttl}")
    private long playerRefreshTokenTtl;

    public String buildStartUrl(String provider, String state, String steamReturnTo) {
        return switch (provider) {
            case PROVIDER_DISCORD -> discordClient.buildAuthorizeUrl(state);
            case PROVIDER_BEATLEADER -> beatLeaderClient.buildAuthorizeUrl(state);
            case PROVIDER_STEAM -> steamClient.buildAuthorizeUrl(steamReturnTo);
            default -> throw new IllegalArgumentException("Unknown provider: " + provider);
        };
    }

    @Transactional
    public DiscordCallbackOutcome handleDiscordCallback(String code, Long linkUserId, String pendingLinkToken,
            String returnTo) {
        DiscordIdentity identity = discordClient.exchangeCode(code);

        if (linkUserId != null) {
            User user = requireUser(linkUserId);
            rejectIfBanned(user);
            OauthConnection conn = addOrRefreshConnection(user, PROVIDER_DISCORD, identity.getId(),
                    identity.displayName(), identity.avatarUrl());
            return new DiscordCallbackOutcome.Session(issueSession(conn));
        }

        if (pendingLinkToken != null) {
            throw new ConflictException("Discord callback cannot carry a pending-link token");
        }

        return oauthConnectionRepository
                .findByProviderAndProviderUserIdAndActiveTrue(PROVIDER_DISCORD, identity.getId())
                .map(conn -> {
                    rejectIfBanned(conn.getUser());
                    conn.setProviderUsername(identity.displayName());
                    conn.setProviderAvatarUrl(identity.avatarUrl());
                    oauthConnectionRepository.save(conn);
                    return (DiscordCallbackOutcome) new DiscordCallbackOutcome.Session(issueSession(conn));
                })
                .orElseGet(() -> new DiscordCallbackOutcome.PendingLink(stateService.createPendingLinkToken(
                        identity.getId(), identity.displayName(), identity.avatarUrl(), returnTo)));
    }

    @Transactional
    public PlayerAuthResponse handleBeatLeaderCallback(String code, Long linkUserId, String pendingLinkToken) {
        BeatLeaderIdentity identity = beatLeaderClient.exchangeCode(code);
        long beatLeaderId;
        try {
            beatLeaderId = Long.parseLong(identity.getId());
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("BeatLeader identity returned a non-numeric player id");
        }
        Long userId = duplicateUserService.resolvePrimaryUserId(beatLeaderId);
        User user = requireUser(userId);

        return completeProviderLogin(user, PROVIDER_BEATLEADER, identity.getId(), identity.getName(),
                identity.getAvatar(), linkUserId, pendingLinkToken);
    }

    @Transactional
    public PlayerAuthResponse handleSteamCallback(Map<String, String> openidParams, Long linkUserId,
            String pendingLinkToken) {
        Long rawSteamId = steamClient.verifyAndExtractSteamId(openidParams);
        Long userId = duplicateUserService.resolvePrimaryUserId(rawSteamId);
        User user = requireUser(userId);

        return completeProviderLogin(user, PROVIDER_STEAM, String.valueOf(rawSteamId), user.getName(),
                user.getAvatarUrl(), linkUserId, pendingLinkToken);
    }

    @Transactional
    public PlayerAuthResponse refresh(String refreshToken) {
        OauthSession session = oauthSessionRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (session.getRefreshTokenExpiresAt().isBefore(Instant.now())) {
            oauthSessionRepository.delete(session);
            throw new UnauthorizedException("Refresh token expired");
        }
        rejectIfBanned(session.getUser());
        return rotateSession(session);
    }

    @Transactional
    public void logout(String refreshToken) {
        oauthSessionRepository.findByRefreshToken(refreshToken).ifPresent(oauthSessionRepository::delete);
    }

    @Transactional(readOnly = true)
    public AuthMeResponse getMe(Long userId) {
        User user = requireUser(userId);

        var connections = oauthConnectionRepository.findByUserIdAndActiveTrue(userId).stream()
                .map(conn -> OauthConnectionSummary.builder()
                        .provider(conn.getProvider())
                        .providerUserId(conn.getProviderUserId())
                        .providerUsername(conn.getProviderUsername())
                        .providerAvatarUrl(conn.getProviderAvatarUrl())
                        .build())
                .toList();

        StaffContext staff = staffUserRepository
                .findByUserIdAndRoleInAndStatusAndActiveTrue(userId, OAUTH_ELIGIBLE_ROLES, StaffUserStatus.ACCEPTED)
                .stream()
                .min(HIGHEST_ROLE_FIRST)
                .map(this::toStaffContext)
                .orElse(null);

        return AuthMeResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .country(user.getCountry())
                .banned(user.isBanned())
                .connections(connections)
                .staff(staff)
                .build();
    }

    @Transactional
    public void removeConnection(Long userId, String provider) {
        OauthConnection conn = oauthConnectionRepository.findByUserIdAndProviderAndActiveTrue(userId, provider)
                .orElseThrow(() -> new ResourceNotFoundException(provider + " connection for user", userId));

        long remaining = oauthConnectionRepository.findByUserIdAndActiveTrue(userId).size();
        if (remaining <= 1) {
            throw new ConflictException("Cannot remove the last OAuth connection for this user");
        }

        conn.setActive(false);
        oauthConnectionRepository.save(conn);
    }

    private PlayerAuthResponse completeProviderLogin(User user, String provider, String providerUserId,
            String providerUsername, String providerAvatarUrl, Long linkUserId, String pendingLinkToken) {
        if (linkUserId != null && !linkUserId.equals(user.getId())) {
            throw new ForbiddenException("Provider account belongs to a different player");
        }
        rejectIfBanned(user);

        OauthConnection providerConn = addOrRefreshConnection(user, provider, providerUserId,
                providerUsername, providerAvatarUrl);

        if (pendingLinkToken != null) {
            PendingLinkClaims pending = stateService.parsePendingLinkToken(pendingLinkToken);
            addOrRefreshConnection(user, PROVIDER_DISCORD, pending.discordId(), pending.discordUsername(),
                    pending.discordAvatarUrl());
        }

        return issueSession(providerConn);
    }

    private OauthConnection addOrRefreshConnection(User user, String provider, String providerUserId,
            String providerUsername, String providerAvatarUrl) {
        oauthConnectionRepository.findByProviderAndProviderUserIdAndActiveTrue(provider, providerUserId)
                .filter(c -> !c.getUser().getId().equals(user.getId()))
                .ifPresent(c -> {
                    throw new ConflictException(provider + " account is linked to a different player");
                });

        OauthConnection conn = oauthConnectionRepository
                .findByUserIdAndProviderAndActiveTrue(user.getId(), provider)
                .orElseGet(() -> OauthConnection.builder()
                        .user(user)
                        .provider(provider)
                        .active(true)
                        .build());
        conn.setProviderUserId(providerUserId);
        conn.setProviderUsername(providerUsername);
        conn.setProviderAvatarUrl(providerAvatarUrl);
        return oauthConnectionRepository.save(conn);
    }

    private PlayerAuthResponse issueSession(OauthConnection anchor) {
        Instant now = Instant.now();
        OauthSession session = OauthSession.builder()
                .user(anchor.getUser())
                .connection(anchor)
                .refreshToken(UUID.randomUUID().toString())
                .refreshTokenExpiresAt(now.plusSeconds(playerRefreshTokenTtl))
                .lastUsedAt(now)
                .build();
        return buildAuthResponse(oauthSessionRepository.save(session));
    }

    private PlayerAuthResponse rotateSession(OauthSession session) {
        Instant now = Instant.now();
        session.setRefreshToken(UUID.randomUUID().toString());
        session.setRefreshTokenExpiresAt(now.plusSeconds(playerRefreshTokenTtl));
        session.setLastUsedAt(now);
        return buildAuthResponse(oauthSessionRepository.save(session));
    }

    private PlayerAuthResponse buildAuthResponse(OauthSession session) {
        Long userId = session.getUser().getId();
        StaffUser oauthStaff = staffUserRepository
                .findByUserIdAndRoleInAndStatusAndActiveTrue(userId, OAUTH_ELIGIBLE_ROLES, StaffUserStatus.ACCEPTED)
                .stream()
                .min(HIGHEST_ROLE_FIRST)
                .orElse(null);

        String accessToken = oauthStaff != null
                ? jwtService.generatePlayerAccessToken(userId, oauthStaff.getId(), oauthStaff.getRole())
                : jwtService.generatePlayerAccessToken(userId);

        return PlayerAuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(session.getRefreshToken())
                .expiresIn(jwtService.getPlayerAccessTokenTtl())
                .userId(userId)
                .build();
    }

    private void rejectIfBanned(User user) {
        if (user.isBanned()) {
            throw new ForbiddenException("This account is banned");
        }
    }

    private User requireUser(Long userId) {
        return userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private StaffContext toStaffContext(StaffUser staffUser) {
        return StaffContext.builder()
                .staffId(staffUser.getId().toString())
                .role(staffUser.getRole().name())
                .status(staffUser.getStatus().name())
                .build();
    }

    public sealed interface DiscordCallbackOutcome {
        record Session(PlayerAuthResponse response) implements DiscordCallbackOutcome {
        }

        record PendingLink(String pendingLinkToken) implements DiscordCallbackOutcome {
        }
    }
}
