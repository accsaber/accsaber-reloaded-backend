package com.accsaber.backend.service.media;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.accsaber.backend.client.BeatLeaderClient;
import com.accsaber.backend.config.CdnProperties;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderLeaderboardResponse;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserSettingKey;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.map.MapRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.UserService;
import com.accsaber.backend.service.player.UserSettingsService;

import java.util.Optional;

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
    private final MapDifficultyRepository mapDifficultyRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final UserSettingsService userSettingsService;
    private final BeatLeaderClient beatLeaderClient;
    private final CdnProperties cdn;

    @Autowired
    @Qualifier("cdnBackfillExecutor")
    private Executor cdnBackfillExecutor;

    @Transactional
    public void mirrorMapCover(UUID mapId) {
        mirrorMapCover(mapId, false);
    }

    @Transactional
    public void mirrorMapCover(UUID mapId, boolean forceRefetch) {
        Map map = mapRepository.findByIdAndActiveTrue(mapId).orElse(null);
        if (map == null) return;
        String upstream = map.getCoverUrl();
        boolean stale = upstream == null || upstream.isBlank() || isCdnUrl(upstream);
        if (stale && !forceRefetch) return;
        if (stale) {
            upstream = fetchCoverUrlFromBeatLeader(map);
            if (upstream == null) return;
        }
        try {
            String cdnUrl = mediaProcessingService.storeFromUrl(upstream, MAP_COVER_SUBDIR, mapId.toString(),
                    MediaFormat.WEBP, cdn.getCoverMaxDimension());
            map.setCoverUrl(cdnUrl);
            mapRepository.save(map);
        } catch (MediaUnavailableException e) {
            log.info("Skipping unavailable cover for map {} ({})", mapId, upstream);
        } catch (RuntimeException e) {
            log.warn("Failed to mirror cover for map {} ({}): {}", mapId, upstream, e.getMessage());
        }
    }

    private String fetchCoverUrlFromBeatLeader(Map map) {
        List<String> leaderboardIds = mapDifficultyRepository.findBlLeaderboardIdsByMapId(map.getId());
        if (leaderboardIds.isEmpty()) {
            log.warn("Cannot fetch cover for map {} — no BL leaderboard id on any difficulty", map.getId());
            return null;
        }
        String leaderboardId = leaderboardIds.get(0);
        Optional<BeatLeaderLeaderboardResponse> lb = beatLeaderClient.getLeaderboard(leaderboardId);
        if (lb.isEmpty() || lb.get().getSong() == null) {
            log.warn("BeatLeader returned no leaderboard/song for id {} (map {})", leaderboardId, map.getId());
            return null;
        }
        var song = lb.get().getSong();
        String coverURL = song.getFullCoverImage();
        if (coverURL == null || coverURL.isBlank()) {
            coverURL = song.getCoverImage();
        }
        if (coverURL == null || coverURL.isBlank()) {
            log.warn("BeatLeader leaderboard {} song has no cover (map {})", leaderboardId, map.getId());
            return null;
        }
        return coverURL;
    }

    @Async("cdnBackfillExecutor")
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
            String cdnUrl = mediaProcessingService.storeFromUrl(upstreamUrl, USER_AVATAR_SUBDIR,
                    String.valueOf(userId), MediaFormat.AVIF, cdn.getAvatarMaxDimension());
            userService.setAvatarFromPlatformSync(userId, cdnUrl, upstreamUrl);
        } catch (MediaUnavailableException e) {
            log.info("Skipping unavailable avatar for user {} ({})", userId, upstreamUrl);
            userService.markAvatarSyncAttempted(userId, upstreamUrl);
        } catch (RuntimeException e) {
            log.warn("Failed to mirror avatar for user {} ({}): {}", userId, upstreamUrl, e.getMessage());
        }
    }

    public String storeUserUploadedAvatar(Long userId, MultipartFile file) {
        String cdnUrl = mediaProcessingService.storeImage(file, USER_AVATAR_SUBDIR,
                String.valueOf(userId), MediaFormat.AVIF);
        userService.setUserUploadedAvatar(userId, cdnUrl);
        userSettingsService.set(userId, UserSettingKey.SYNC_AVATAR, false);
        return cdnUrl;
    }

    @Async("cdnBackfillExecutor")
    public void backfillAllMapCovers(boolean force) {
        List<Map> maps = mapRepository.findByActiveTrue();
        log.info("CDN backfill: starting cover backfill for {} maps (force={})", maps.size(), force);
        AtomicInteger done = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        runParallel(maps, map -> {
            boolean fileGood = mediaProcessingService.fileExistsAndNonEmpty(MAP_COVER_SUBDIR, map.getId().toString(), MediaFormat.WEBP);
            if (!force && fileGood && isCdnUrl(map.getCoverUrl())) {
                skipped.incrementAndGet();
                return;
            }
            mirrorMapCover(map.getId(), force || !fileGood);
            done.incrementAndGet();
        });
        log.info("CDN backfill: covers done ({} processed, {} skipped)", done.get(), skipped.get());
    }

    @Async("cdnBackfillExecutor")
    public void backfillAllUserAvatars(boolean force) {
        List<User> users = userRepository.findByActiveTrue();
        log.info("CDN backfill: starting avatar backfill for {} users (force={})", users.size(), force);
        AtomicInteger done = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        runParallel(users, user -> {
            boolean syncEnabled = userSettingsService.get(user.getId(), UserSettingKey.SYNC_AVATAR, Boolean.class);
            if (!syncEnabled) {
                skipped.incrementAndGet();
                return;
            }
            String upstream = user.getLastSyncedAvatarUrl() != null
                    ? user.getLastSyncedAvatarUrl()
                    : user.getAvatarUrl();
            if (upstream == null || upstream.isBlank() || isCdnUrl(upstream)) {
                skipped.incrementAndGet();
                return;
            }
            boolean fileGood = mediaProcessingService.fileExistsAndNonEmpty(USER_AVATAR_SUBDIR, String.valueOf(user.getId()), MediaFormat.AVIF);
            if (!force && fileGood && isCdnUrl(user.getCdnAvatarUrl())) {
                skipped.incrementAndGet();
                return;
            }
            mirrorUserAvatar(user.getId(), upstream, force || !fileGood);
            done.incrementAndGet();
        });
        log.info("CDN backfill: avatars done ({} processed, {} skipped)", done.get(), skipped.get());
    }

    private <T> void runParallel(List<T> items, java.util.function.Consumer<T> action) {
        items.stream()
                .map(item -> CompletableFuture.runAsync(() -> {
                    try {
                        action.accept(item);
                    } catch (RuntimeException e) {
                        log.warn("backfill task failed: {}", e.getMessage());
                    }
                    throttle();
                }, cdnBackfillExecutor))
                .toList()
                .forEach(CompletableFuture::join);
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
