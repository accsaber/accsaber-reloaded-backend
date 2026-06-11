package com.accsaber.backend.service.media;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.accsaber.backend.config.CdnProperties;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserSettingKey;
import com.accsaber.backend.repository.map.MapRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.UserService;
import com.accsaber.backend.service.player.UserSettingsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CdnSyncService {

    private static final String MAP_COVER_SUBDIR = "covers";
    private static final String USER_AVATAR_SUBDIR = "avatars";
    private static final String BL_AVATAR_SENTINEL = "steamavatar.png";

    private final MediaProcessingService mediaProcessingService;
    private final MapRepository mapRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final UserSettingsService userSettingsService;
    private final CdnProperties cdn;

    @Transactional
    public void mirrorMapCover(UUID mapId) {
        Map map = mapRepository.findByIdAndActiveTrue(mapId).orElse(null);
        if (map == null) return;
        String upstream = map.getCoverUrl();
        if (upstream == null || upstream.isBlank() || isCdnUrl(upstream)) return;
        try {
            String cdnUrl = mediaProcessingService.storeFromUrl(upstream, MAP_COVER_SUBDIR, mapId.toString());
            map.setCoverUrl(cdnUrl);
            mapRepository.save(map);
        } catch (MediaUnavailableException e) {
            log.info("Skipping unavailable cover for map {} ({})", mapId, upstream);
        } catch (RuntimeException e) {
            log.warn("Failed to mirror cover for map {} ({}): {}", mapId, upstream, e.getMessage());
        }
    }

    @Async("backfillExecutor")
    public void mirrorMapCoverAsync(UUID mapId) {
        mirrorMapCover(mapId);
    }

    public void mirrorUserAvatarIfChanged(Long userId, String upstreamUrl) {
        mirrorUserAvatar(userId, upstreamUrl, false);
    }

    public void mirrorUserAvatar(Long userId, String upstreamUrl, boolean forceRetry) {
        if (upstreamUrl == null || upstreamUrl.isBlank()) return;
        boolean syncEnabled = userSettingsService.get(userId, UserSettingKey.SYNC_AVATAR, Boolean.class);
        if (!syncEnabled) return;
        User user = userRepository.findByIdAndActiveTrue(userId).orElse(null);
        if (user == null) return;
        if (!forceRetry && Objects.equals(upstreamUrl, user.getLastSyncedAvatarUrl())) {
            return;
        }
        if (upstreamUrl.endsWith(BL_AVATAR_SENTINEL)) {
            userService.markAvatarSyncAttempted(userId, upstreamUrl);
            return;
        }
        try {
            String cdnUrl = mediaProcessingService.storeFromUrl(upstreamUrl, USER_AVATAR_SUBDIR, String.valueOf(userId));
            userService.setAvatarFromPlatformSync(userId, cdnUrl, upstreamUrl);
        } catch (MediaUnavailableException e) {
            log.info("Skipping unavailable avatar for user {} ({})", userId, upstreamUrl);
            userService.markAvatarSyncAttempted(userId, upstreamUrl);
        } catch (RuntimeException e) {
            log.warn("Failed to mirror avatar for user {} ({}): {}", userId, upstreamUrl, e.getMessage());
        }
    }

    public String storeUserUploadedAvatar(Long userId, MultipartFile file) {
        String cdnUrl = mediaProcessingService.storeImage(file, USER_AVATAR_SUBDIR, String.valueOf(userId));
        userService.setUserUploadedAvatar(userId, cdnUrl);
        userSettingsService.set(userId, UserSettingKey.SYNC_AVATAR, false);
        return cdnUrl;
    }

    @Async("backfillExecutor")
    public void backfillAllMapCovers(boolean force) {
        List<Map> maps = mapRepository.findByActiveTrue();
        log.info("CDN backfill: starting cover backfill for {} maps (force={})", maps.size(), force);
        int done = 0;
        int skipped = 0;
        for (Map map : maps) {
            if (!force && mediaProcessingService.fileExists(MAP_COVER_SUBDIR, map.getId().toString())
                    && isCdnUrl(map.getCoverUrl())) {
                skipped++;
                continue;
            }
            mirrorMapCover(map.getId());
            done++;
            throttle();
        }
        log.info("CDN backfill: covers done ({} processed, {} skipped)", done, skipped);
    }

    @Async("backfillExecutor")
    public void backfillAllUserAvatars(boolean force) {
        List<User> users = userRepository.findByActiveTrue();
        log.info("CDN backfill: starting avatar backfill for {} users (force={})", users.size(), force);
        int done = 0;
        int skipped = 0;
        for (User user : users) {
            boolean syncEnabled = userSettingsService.get(user.getId(), UserSettingKey.SYNC_AVATAR, Boolean.class);
            if (!syncEnabled) {
                skipped++;
                continue;
            }
            String upstream = user.getLastSyncedAvatarUrl() != null
                    ? user.getLastSyncedAvatarUrl()
                    : user.getAvatarUrl();
            if (upstream == null || upstream.isBlank() || isCdnUrl(upstream)) {
                skipped++;
                continue;
            }
            if (!force && mediaProcessingService.fileExists(USER_AVATAR_SUBDIR, String.valueOf(user.getId()))
                    && isCdnUrl(user.getAvatarUrl())) {
                skipped++;
                continue;
            }
            mirrorUserAvatar(user.getId(), upstream, force);
            done++;
            throttle();
        }
        log.info("CDN backfill: avatars done ({} processed, {} skipped)", done, skipped);
    }

    private boolean isCdnUrl(String url) {
        return url != null && url.startsWith(cdn.getBaseUrl());
    }

    private void throttle() {
        long delay = cdn.getBackfillDelayMs();
        if (delay <= 0) return;
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
