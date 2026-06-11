package com.accsaber.backend.service.player;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.accsaber.backend.client.BeatLeaderClient;
import com.accsaber.backend.client.ScoreSaberClient;
import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderPlayerResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberPlayerResponse;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserSettingKey;
import com.accsaber.backend.service.media.CdnSyncService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerImportService {

        private final BeatLeaderClient beatLeaderClient;
        private final ScoreSaberClient scoreSaberClient;
        private final UserService userService;
        private final UserSettingsService userSettingsService;
        private final CdnSyncService cdnSyncService;

        public User ensurePlayerExists(Long userId) {
                Optional<User> existing = userService.findOptionalByUserId(userId);
                if (existing.isPresent()) {
                        return existing.get();
                }

                String userIdStr = String.valueOf(userId);
                Optional<BeatLeaderPlayerResponse> blProfile = beatLeaderClient.getPlayer(userIdStr);
                Optional<ScoreSaberPlayerResponse> ssProfile = blProfile.isPresent()
                                ? Optional.empty()
                                : scoreSaberClient.getPlayer(userIdStr);

                String name = blProfile.map(BeatLeaderPlayerResponse::getName)
                                .or(() -> ssProfile.map(ScoreSaberPlayerResponse::getName))
                                .orElse("Unknown");

                String avatarUrl = blProfile.map(BeatLeaderPlayerResponse::getAvatar)
                                .or(() -> ssProfile.map(ScoreSaberPlayerResponse::getAvatar))
                                .orElse(null);

                String country = blProfile.map(BeatLeaderPlayerResponse::getCountry)
                                .filter(c -> !c.isBlank() && !c.equalsIgnoreCase("not set"))
                                .or(() -> ssProfile.map(ScoreSaberPlayerResponse::getCountry))
                                .orElse(null);

                log.info("Creating new user {} ({})", name, userId);
                User created;
                try {
                        created = userService.createUser(userId, name, avatarUrl, country);
                } catch (ConflictException e) {
                        created = userService.findOptionalByUserId(userId)
                                        .orElseThrow(() -> new ResourceNotFoundException("User", userId));
                }
                if (avatarUrl != null) {
                        cdnSyncService.mirrorUserAvatarIfChanged(userId, avatarUrl);
                }
                return created;
        }

        public void refreshPlayerProfile(Long userId) {
                String userIdStr = String.valueOf(userId);
                Optional<BeatLeaderPlayerResponse> blProfile = beatLeaderClient.getPlayer(userIdStr);
                Optional<ScoreSaberPlayerResponse> ssProfile = blProfile.isPresent()
                                ? Optional.empty()
                                : scoreSaberClient.getPlayer(userIdStr);

                if (blProfile.isEmpty() && ssProfile.isEmpty()) {
                        log.warn("Both platforms returned 404 for player {}", userId);
                        return;
                }

                boolean nameSyncEnabled = userSettingsService.get(userId, UserSettingKey.SYNC_NAME, Boolean.class);
                String name = nameSyncEnabled
                                ? blProfile.map(BeatLeaderPlayerResponse::getName)
                                                .or(() -> ssProfile.map(ScoreSaberPlayerResponse::getName))
                                                .orElse(null)
                                : null;

                String avatarUrl = blProfile.map(BeatLeaderPlayerResponse::getAvatar)
                                .or(() -> ssProfile.map(ScoreSaberPlayerResponse::getAvatar))
                                .orElse(null);

                boolean countryOverridden = userService.findOptionalByUserId(userId)
                                .map(User::isCountryOverride)
                                .orElse(false);

                String country = countryOverridden ? null
                                : blProfile.map(BeatLeaderPlayerResponse::getCountry)
                                                .filter(c -> !c.isBlank() && !c.equalsIgnoreCase("not set"))
                                                .or(() -> ssProfile.map(ScoreSaberPlayerResponse::getCountry))
                                                .orElse(null);

                boolean playerInactive = blProfile.isPresent()
                                ? blProfile
                                                .map(BeatLeaderPlayerResponse::getScoreStats)
                                                .map(BeatLeaderPlayerResponse.ScoreStats::getLastScoreTime)
                                                .map(epoch -> Instant.ofEpochSecond(epoch)
                                                                .isBefore(Instant.now().minus(Duration.ofDays(90))))
                                                .orElse(true)
                                : ssProfile.map(ScoreSaberPlayerResponse::isInactive).orElse(true);
                userService.updateProfile(userId, name, null, country, playerInactive);
                if (avatarUrl != null) {
                        cdnSyncService.mirrorUserAvatarIfChanged(userId, avatarUrl);
                }
                log.debug("Refreshed profile for player {}", userId);
        }
}
