package com.accsaber.backend.service.discord;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.request.discord.LinkDiscordRequest;
import com.accsaber.backend.model.dto.request.discord.UpdateDiscordLinkRequest;
import com.accsaber.backend.model.dto.response.DiscordLinkResponse;
import com.accsaber.backend.model.entity.user.OauthConnection;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.user.OauthConnectionRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.util.ProfileUrlResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordLinkService {

    private static final String PROVIDER = "discord";

    private final OauthConnectionRepository oauthConnectionRepository;
    private final UserRepository userRepository;
    private final DuplicateUserService duplicateUserService;
    private final ProfileUrlResolver profileUrlResolver;

    @Transactional
    public DiscordLinkResponse link(LinkDiscordRequest request) {
        if (oauthConnectionRepository
                .existsByProviderAndProviderUserIdAndActiveTrue(PROVIDER, request.getDiscordId())) {
            throw new ConflictException("Discord account is already linked", request.getDiscordId());
        }

        String platformId = profileUrlResolver.resolve(request.getProfileUrl());
        Long userId = duplicateUserService.resolvePrimaryUserId(Long.parseLong(platformId));

        if (oauthConnectionRepository.existsByUserIdAndProviderAndActiveTrue(userId, PROVIDER)) {
            throw new ConflictException("Player is already linked to a Discord account", userId);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        OauthConnection connection = OauthConnection.builder()
                .user(user)
                .provider(PROVIDER)
                .providerUserId(request.getDiscordId())
                .active(true)
                .build();
        oauthConnectionRepository.save(connection);

        log.info("Linked Discord {} to user {}", request.getDiscordId(), userId);

        return toResponse(connection);
    }

    @Transactional(readOnly = true)
    public DiscordLinkResponse findByDiscordId(String discordId) {
        OauthConnection connection = oauthConnectionRepository
                .findByProviderAndProviderUserIdAndActiveTrue(PROVIDER, discordId)
                .orElseThrow(() -> new ResourceNotFoundException("Discord link", discordId));
        return toResponse(connection);
    }

    @Transactional(readOnly = true)
    public DiscordLinkResponse findByUserId(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        OauthConnection connection = oauthConnectionRepository
                .findByUserIdAndProviderAndActiveTrue(resolved, PROVIDER)
                .orElseThrow(() -> new ResourceNotFoundException("Discord link for user", resolved));
        return toResponse(connection);
    }

    @Transactional
    public void unlink(String discordId) {
        OauthConnection connection = oauthConnectionRepository
                .findByProviderAndProviderUserIdAndActiveTrue(PROVIDER, discordId)
                .orElseThrow(() -> new ResourceNotFoundException("Discord link", discordId));
        oauthConnectionRepository.delete(connection);
        log.info("Unlinked Discord {}", discordId);
    }

    @Transactional
    public DiscordLinkResponse update(String discordId, UpdateDiscordLinkRequest request) {
        OauthConnection connection = oauthConnectionRepository
                .findByProviderAndProviderUserIdAndActiveTrue(PROVIDER, discordId)
                .orElseThrow(() -> new ResourceNotFoundException("Discord link", discordId));

        String platformId = profileUrlResolver.resolve(request.getProfileUrl());
        Long newUserId = duplicateUserService.resolvePrimaryUserId(Long.parseLong(platformId));
        Long oldUserId = connection.getUser().getId();

        if (!newUserId.equals(oldUserId)
                && oauthConnectionRepository.existsByUserIdAndProviderAndActiveTrue(newUserId, PROVIDER)) {
            throw new ConflictException("Player is already linked to a Discord account", newUserId);
        }

        User newUser = userRepository.findById(newUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", newUserId));
        connection.setUser(newUser);
        oauthConnectionRepository.save(connection);

        log.info("Updated Discord {} link from user {} to user {}", discordId, oldUserId, newUserId);

        return toResponse(connection);
    }

    private DiscordLinkResponse toResponse(OauthConnection connection) {
        return DiscordLinkResponse.builder()
                .discordId(connection.getProviderUserId())
                .userId(String.valueOf(connection.getUser().getId()))
                .playerName(connection.getUser().getName())
                .createdAt(connection.getLinkedAt())
                .build();
    }
}
