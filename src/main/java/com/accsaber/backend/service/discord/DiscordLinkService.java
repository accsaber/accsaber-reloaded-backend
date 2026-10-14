package com.accsaber.backend.service.discord;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.request.discord.UpdateDiscordLinkRequest;
import com.accsaber.backend.model.dto.response.DiscordLinkResponse;
import com.accsaber.backend.model.entity.user.OauthConnection;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.user.OauthConnectionRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.player.ProfileUrlResolver;

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

    @Transactional(readOnly = true)
    public DiscordLinkResponse findByDiscordId(String discordId) {
        OauthConnection connection = requireLink(discordId);
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
        OauthConnection connection = requireLink(discordId);
        oauthConnectionRepository.delete(connection);
        log.info("Unlinked Discord {}", discordId);
    }

    @Transactional
    public DiscordLinkResponse update(String discordId, UpdateDiscordLinkRequest request) {
        OauthConnection connection = requireLink(discordId);

        String platformId = profileUrlResolver.resolve(request.profileUrl());
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

    private OauthConnection requireLink(String discordId) {
        return oauthConnectionRepository.findByProviderAndProviderUserIdAndActiveTrue(PROVIDER, discordId)
                .orElseThrow(() -> new ResourceNotFoundException("Discord link", discordId));
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
