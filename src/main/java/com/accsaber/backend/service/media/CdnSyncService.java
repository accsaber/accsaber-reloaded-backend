package com.accsaber.backend.service.media;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.accsaber.backend.client.BeatLeaderClient;
import com.accsaber.backend.client.ScoreSaberClient;
import com.accsaber.backend.config.CdnProperties;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderLeaderboardResponse;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderPlayerResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberPlayerResponse;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserSettingKey;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.map.MapRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.UserService;
import com.accsaber.backend.service.player.UserSettingsService;
import com.accsaber.backend.util.Texts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CdnSyncService {

    private static final String MAP_COVER_SUBDIR = "covers";
    private static final String USER_AVATAR_SUBDIR = "avatars";
    private static final String BL_AVATAR_SENTINEL = "steamavatar.png";
    private static final int SETTINGS_BATCH = 10_000;

    private final MediaProcessingService mediaProcessingService;
    private final MapRepository mapRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final UserSettingsService userSettingsService;
    private final BeatLeaderClient beatLeaderClient;
    private final ScoreSaberClient scoreSaberClient;
    private final CdnProperties cdn;
    @Qualifier("cdnBackfillExecutor")
    private final Executor cdnBackfillExecutor;

    public void mirrorMapCover(UUID mapId) {
        Map map = mapRepository.findByIdAndActiveTrue(mapId).orElse(null);
        if (map == null) return;
        BeatLeaderLeaderboardResponse.Song song = fetchSongFromBeatLeader(map);
        if (song == null) return;

        String beatSaverUrl = Texts.blankToNull(song.getCoverImage());
        String mirrorSource = Texts.blankToNull(song.getFullCoverImage());
        if (mirrorSource == null) mirrorSource = beatSaverUrl;
        if (beatSaverUrl == null) beatSaverUrl = mirrorSource;
        if (mirrorSource == null) {
            log.warn("BeatLeader leaderboard for map {} has no cover URL", mapId);
            return;
        }

        try {
            String cdnUrl = mediaProcessingService.storeFromUrl(mirrorSource, MAP_COVER_SUBDIR, mapId.toString(),
                    MediaFormat.WEBP, cdn.getCoverMaxDimension());
            map.setCoverUrl(beatSaverUrl);
            map.setCdnCoverUrl(cdnUrl);
            mapRepository.save(map);
        } catch (MediaUnavailableException e) {
            log.info("Skipping unavailable cover for map {} ({})", mapId, mirrorSource);
        } catch (RuntimeException e) {
            log.warn("Failed to mirror cover for map {} ({}): {}", mapId, mirrorSource, e.getMessage());
        }
    }

    @Async("cdnBackfillExecutor")
    public void mirrorMapCoverAsync(UUID mapId) {
        mirrorMapCover(mapId);
    }

    private BeatLeaderLeaderboardResponse.Song fetchSongFromBeatLeader(Map map) {
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
        return lb.get().getSong();
    }

    public void mirrorUserAvatarIfChanged(Long userId, String upstreamUrl) {
        if (upstreamUrl == null || upstreamUrl.isBlank()) return;
        boolean syncEnabled = userSettingsService.get(userId, UserSettingKey.SYNC_AVATAR, Boolean.class);
        if (!syncEnabled) return;
        User user = userRepository.findByIdAndActiveTrue(userId).orElse(null);
        if (user == null) return;
        if (Objects.equals(upstreamUrl, user.getLastSyncedAvatarUrl())) {
            return;
        }
        mirrorUserAvatar(userId, upstreamUrl);
    }

    private void mirrorUserAvatar(Long userId, String upstreamUrl) {
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
                String.valueOf(userId), MediaFormat.AVIF, cdn.getAvatarMaxDimension());
        userService.setUserUploadedAvatar(userId, cdnUrl);
        userSettingsService.set(userId, UserSettingKey.SYNC_AVATAR, false);
        return cdnUrl;
    }

    private String fetchFreshAvatarUrl(Long userId) {
        String userIdStr = String.valueOf(userId);
        return beatLeaderClient.getPlayer(userIdStr)
                .map(BeatLeaderPlayerResponse::getAvatar)
                .filter(s -> s != null && !s.isBlank())
                .or(() -> scoreSaberClient.getPlayer(userIdStr)
                        .map(ScoreSaberPlayerResponse::getAvatar)
                        .filter(s -> s != null && !s.isBlank()))
                .orElse(null);
    }

    @Async("cdnBackfillExecutor")
    public CompletableFuture<Void> backfillAllMapCovers(boolean force) {
        List<Map> maps = mapRepository.findByActiveTrue();
        log.info("CDN backfill: starting cover backfill for {} maps (force={})", maps.size(), force);
        AtomicInteger done = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        runParallel(maps, map -> {
            boolean fileGood = mediaProcessingService.fileExistsAndNonEmpty(
                    MAP_COVER_SUBDIR, map.getId().toString(), MediaFormat.WEBP);
            if (!force && fileGood && cdn.isCdnUrl(map.getCdnCoverUrl())) {
                skipped.incrementAndGet();
                return;
            }
            mirrorMapCover(map.getId());
            done.incrementAndGet();
        });
        log.info("CDN backfill: covers done ({} processed, {} skipped)", done.get(), skipped.get());
        return CompletableFuture.completedFuture(null);
    }

    @Async("cdnBackfillExecutor")
    public CompletableFuture<Void> backfillAllUserAvatars(boolean force) {
        List<User> users = userRepository.findByActiveTrue();
        log.info("CDN backfill: starting avatar backfill for {} users (force={})", users.size(), force);
        java.util.Map<Long, Boolean> syncEnabled = loadSyncAvatar(users);
        AtomicInteger done = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        runParallel(users, user -> {
            if (!syncEnabled.get(user.getId())) {
                skipped.incrementAndGet();
                return;
            }
            boolean fileGood = mediaProcessingService.fileExistsAndNonEmpty(
                    USER_AVATAR_SUBDIR, String.valueOf(user.getId()));
            if (!force && fileGood && cdn.isCdnUrl(user.getCdnAvatarUrl())) {
                skipped.incrementAndGet();
                return;
            }
            String upstream = fetchFreshAvatarUrl(user.getId());
            if (upstream == null) {
                skipped.incrementAndGet();
                return;
            }
            mirrorUserAvatar(user.getId(), upstream);
            done.incrementAndGet();
        });
        log.info("CDN backfill: avatars done ({} processed, {} skipped)", done.get(), skipped.get());
        return CompletableFuture.completedFuture(null);
    }

    private java.util.Map<Long, Boolean> loadSyncAvatar(List<User> users) {
        java.util.Map<Long, Boolean> enabled = new HashMap<>();
        List<Long> ids = users.stream().map(User::getId).toList();
        for (int from = 0; from < ids.size(); from += SETTINGS_BATCH) {
            enabled.putAll(userSettingsService.getMany(ids.subList(from, Math.min(from + SETTINGS_BATCH, ids.size())),
                    UserSettingKey.SYNC_AVATAR, Boolean.class));
        }
        return enabled;
    }

    private <T> void runParallel(List<T> items, Consumer<T> action) {
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
