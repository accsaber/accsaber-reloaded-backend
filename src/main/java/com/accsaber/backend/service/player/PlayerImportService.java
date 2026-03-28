package com.accsaber.backend.service.player;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Service;

import com.accsaber.backend.client.BeatLeaderClient;
import com.accsaber.backend.client.ScoreSaberClient;
import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderPlayerResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberPlayerResponse;
import com.accsaber.backend.model.entity.user.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerImportService {

        private final BeatLeaderClient beatLeaderClient;
        private final ScoreSaberClient scoreSaberClient;
        private final UserService userService;

        public User ensurePlayerExists(Long userId) {
                Optional<User> existing = userService.findOptionalByUserId(userId);
                if (existing.isPresent()) {
                        return existing.get();
                }

                String userIdStr = String.valueOf(userId);
                CompletableFuture<Optional<BeatLeaderPlayerResponse>> blFuture = CompletableFuture
                                .supplyAsync(() -> beatLeaderClient.getPlayer(userIdStr));
                CompletableFuture<Optional<ScoreSaberPlayerResponse>> ssFuture = CompletableFuture
                                .supplyAsync(() -> scoreSaberClient.getPlayer(userIdStr));
                Optional<BeatLeaderPlayerResponse> blProfile = blFuture.join();
                Optional<ScoreSaberPlayerResponse> ssProfile = ssFuture.join();

                String name = blProfile.map(BeatLeaderPlayerResponse::getName)
                                .or(() -> ssProfile.map(ScoreSaberPlayerResponse::getName))
                                .orElse("Unknown");

                String avatarUrl = blProfile.map(BeatLeaderPlayerResponse::getAvatar)
                                .or(() -> ssProfile.map(ScoreSaberPlayerResponse::getProfilePicture))
                                .orElse(null);

                String country = blProfile.map(BeatLeaderPlayerResponse::getCountry)
                                .filter(c -> !c.isBlank() && !c.equalsIgnoreCase("not set"))
                                .or(() -> ssProfile.map(ScoreSaberPlayerResponse::getCountry))
                                .orElse(null);

                log.info("Creating new user {} ({})", name, userId);
                try {
                        return userService.createUser(userId, name, avatarUrl, country);
                } catch (ConflictException e) {
                        return userService.findOptionalByUserId(userId)
                                        .orElseThrow(() -> new ResourceNotFoundException("User", userId));
                }
        }

        public void refreshPlayerProfile(Long userId) {
                String userIdStr = String.valueOf(userId);
                Optional<ScoreSaberPlayerResponse> ssProfile = scoreSaberClient.getPlayer(userIdStr);
                Optional<BeatLeaderPlayerResponse> blProfile = beatLeaderClient.getPlayer(userIdStr);

                if (ssProfile.isEmpty() && blProfile.isEmpty()) {
                        log.warn("Both platforms returned 404 for player {}", userId);
                        return;
                }

                String name = blProfile.map(BeatLeaderPlayerResponse::getName)
                                .or(() -> ssProfile.map(ScoreSaberPlayerResponse::getName))
                                .orElse(null);

                String avatarUrl = blProfile.map(BeatLeaderPlayerResponse::getAvatar)
                                .or(() -> ssProfile.map(ScoreSaberPlayerResponse::getProfilePicture))
                                .orElse(null);

                boolean countryOverridden = userService.findOptionalByUserId(userId)
                                .map(User::isCountryOverride)
                                .orElse(false);

                String country = countryOverridden ? null
                                : blProfile.map(BeatLeaderPlayerResponse::getCountry)
                                                .filter(c -> !c.isBlank() && !c.equalsIgnoreCase("not set"))
                                                .or(() -> ssProfile.map(ScoreSaberPlayerResponse::getCountry))
                                                .orElse(null);

                Boolean ssInactive = ssProfile.map(ScoreSaberPlayerResponse::isInactive).orElse(null);
                userService.updateProfile(userId, name, avatarUrl, country, ssInactive);
                log.debug("Refreshed profile for player {}", userId);
        }
}
