package com.accsaber.backend.service.discord;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.client.BeatLeaderClient;
import com.accsaber.backend.client.ScoreSaberClient;
import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.discord.LinkDiscordRequest;
import com.accsaber.backend.model.dto.request.discord.UpdateDiscordLinkRequest;
import com.accsaber.backend.model.dto.response.DiscordLinkResponse;
import com.accsaber.backend.model.entity.user.DiscordUserLink;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.user.DiscordUserLinkRepository;
import com.accsaber.backend.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordLinkService {

    private static final Pattern BEATLEADER_PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.)?beatleader\\.xyz/u/([\\w-]+)");
    private static final Pattern SCORESABER_PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.)?scoresaber\\.com/u/(\\d+)");

    private final DiscordUserLinkRepository discordUserLinkRepository;
    private final UserRepository userRepository;
    private final BeatLeaderClient beatLeaderClient;
    private final ScoreSaberClient scoreSaberClient;

    @Transactional
    public DiscordLinkResponse link(LinkDiscordRequest request) {
        if (discordUserLinkRepository.existsById(request.getDiscordId())) {
            throw new ConflictException("Discord account is already linked", request.getDiscordId());
        }

        String steamId = extractSteamId(request.getProfileUrl());
        Long userId = Long.parseLong(steamId);

        if (discordUserLinkRepository.existsByUserId(userId)) {
            throw new ConflictException("Player is already linked to a Discord account", userId);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        DiscordUserLink link = DiscordUserLink.builder()
                .discordId(request.getDiscordId())
                .user(user)
                .build();
        discordUserLinkRepository.save(link);

        log.info("Linked Discord {} to user {}", request.getDiscordId(), userId);

        return toResponse(link);
    }

    @Transactional(readOnly = true)
    public DiscordLinkResponse findByDiscordId(String discordId) {
        DiscordUserLink link = discordUserLinkRepository.findById(discordId)
                .orElseThrow(() -> new ResourceNotFoundException("Discord link", discordId));
        return toResponse(link);
    }

    @Transactional(readOnly = true)
    public DiscordLinkResponse findByUserId(Long userId) {
        DiscordUserLink link = discordUserLinkRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Discord link for user", userId));
        return toResponse(link);
    }

    @Transactional
    public void unlink(String discordId) {
        if (!discordUserLinkRepository.existsById(discordId)) {
            throw new ResourceNotFoundException("Discord link", discordId);
        }
        discordUserLinkRepository.deleteById(discordId);
        log.info("Unlinked Discord {}", discordId);
    }

    @Transactional
    public DiscordLinkResponse update(String discordId, UpdateDiscordLinkRequest request) {
        DiscordUserLink link = discordUserLinkRepository.findById(discordId)
                .orElseThrow(() -> new ResourceNotFoundException("Discord link", discordId));

        String steamId = extractSteamId(request.getProfileUrl());
        Long newUserId = Long.parseLong(steamId);

        if (!newUserId.equals(link.getUser().getId())
                && discordUserLinkRepository.existsByUserId(newUserId)) {
            throw new ConflictException("Player is already linked to a Discord account", newUserId);
        }

        User newUser = userRepository.findById(newUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", newUserId));
        Long oldUserId = link.getUser().getId();
        link.setUser(newUser);
        discordUserLinkRepository.save(link);

        log.info("Updated Discord {} link from user {} to user {}", discordId, oldUserId, newUserId);

        return toResponse(link);
    }

    private String extractSteamId(String profileUrl) {
        Matcher blMatcher = BEATLEADER_PATTERN.matcher(profileUrl);
        if (blMatcher.find()) {
            String identifier = blMatcher.group(1);
            return beatLeaderClient.getPlayer(identifier)
                    .orElseThrow(() -> new ResourceNotFoundException("BeatLeader player", identifier))
                    .getId();
        }

        Matcher ssMatcher = SCORESABER_PATTERN.matcher(profileUrl);
        if (ssMatcher.find()) {
            String id = ssMatcher.group(1);
            scoreSaberClient.getPlayer(id)
                    .orElseThrow(() -> new ResourceNotFoundException("ScoreSaber player", id));
            return id;
        }

        throw new ValidationException("Invalid profile URL. Use a BeatLeader or ScoreSaber profile link.");
    }

    private DiscordLinkResponse toResponse(DiscordUserLink link) {
        return DiscordLinkResponse.builder()
                .discordId(link.getDiscordId())
                .userId(link.getUser().getId())
                .playerName(link.getUser().getName())
                .createdAt(link.getCreatedAt())
                .build();
    }
}
