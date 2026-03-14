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

        public User ensurePlayerExists(Long steamId) {
                Optional<User> existing = userService.findOptionalBySteamId(steamId);
                if (existing.isPresent()) {
                        return existing.get();
                }

                String steamIdStr = String.valueOf(steamId);
                CompletableFuture<Optional<BeatLeaderPlayerResponse>> blFuture = CompletableFuture
                                .supplyAsync(() -> beatLeaderClient.getPlayer(steamIdStr));
                CompletableFuture<Optional<ScoreSaberPlayerResponse>> ssFuture = CompletableFuture
                                .supplyAsync(() -> scoreSaberClient.getPlayer(steamIdStr));
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

                log.info("Creating new user {} ({})", name, steamId);
                try {
                        return userService.createUser(steamId, name, avatarUrl, country);
                } catch (ConflictException e) {
                        return userService.findOptionalBySteamId(steamId)
                                        .orElseThrow(() -> new ResourceNotFoundException("User", steamId));
                }
        }

        public void refreshPlayerProfile(Long steamId) {
                String steamIdStr = String.valueOf(steamId);
                Optional<ScoreSaberPlayerResponse> ssProfile = scoreSaberClient.getPlayer(steamIdStr);
                Optional<BeatLeaderPlayerResponse> blProfile = beatLeaderClient.getPlayer(steamIdStr);

                if (ssProfile.isEmpty() && blProfile.isEmpty()) {
                        log.warn("Both platforms returned 404 for player {}", steamId);
                        return;
                }

                String name = blProfile.map(BeatLeaderPlayerResponse::getName)
                                .or(() -> ssProfile.map(ScoreSaberPlayerResponse::getName))
                                .orElse(null);

                String avatarUrl = blProfile.map(BeatLeaderPlayerResponse::getAvatar)
                                .or(() -> ssProfile.map(ScoreSaberPlayerResponse::getProfilePicture))
                                .orElse(null);

                String country = blProfile.map(BeatLeaderPlayerResponse::getCountry)
                                .filter(c -> !c.isBlank() && !c.equalsIgnoreCase("not set"))
                                .or(() -> ssProfile.map(ScoreSaberPlayerResponse::getCountry))
                                .orElse(null);

                userService.updateProfile(steamId, name, avatarUrl, country);
                log.debug("Refreshed profile for player {}", steamId);
        }
}
