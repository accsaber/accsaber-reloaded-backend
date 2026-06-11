package com.accsaber.backend.service.media;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.accsaber.backend.config.CdnProperties;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserSettingKey;
import com.accsaber.backend.repository.campaign.CampaignRepository;
import com.accsaber.backend.repository.item.ItemRepository;
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
    private static final String CAMPAIGN_BG_SUBDIR = "campaigns";
    private static final String CAMPAIGN_ICON_SUBDIR = "campaign-icons";
    private static final String ITEM_ICON_SUBDIR = "items";
    private static final String BL_AVATAR_SENTINEL = "steamavatar.png";

    private final MediaProcessingService mediaProcessingService;
    private final MapRepository mapRepository;
    private final UserRepository userRepository;
    private final CampaignRepository campaignRepository;
    private final ItemRepository itemRepository;
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

    @Async("backfillExecutor")
    public void regenerateAllStaticVariants(boolean skipAvatars) {
        log.info("CDN regen: starting static-variant regeneration (skipAvatars={})", skipAvatars);
        int avatars = skipAvatars ? 0 : regenerateAvatarsStaticVariants();
        int covers = regenerateCoversStaticVariants();
        int campaignBgs = regenerateCampaignBackgroundsStaticVariants();
        int campaignIcons = regenerateCampaignIconsStaticVariants();
        int items = regenerateItemIconsStaticVariants();
        log.info("CDN regen: complete (avatars={}, covers={}, campaignBgs={}, campaignIcons={}, items={})",
                avatars, covers, campaignBgs, campaignIcons, items);
    }

    private int regenerateAvatarsStaticVariants() {
        int updated = 0;
        for (User user : userRepository.findByActiveTrue()) {
            String key = String.valueOf(user.getId());
            if (!mediaProcessingService.avifExists(USER_AVATAR_SUBDIR, key)) continue;
            try {
                String newUrl = mediaProcessingService.regenerateStaticVariant(USER_AVATAR_SUBDIR, key);
                if (newUrl != null && !newUrl.equals(user.getAvatarUrl())) {
                    userService.setAvatarFromPlatformSync(user.getId(), newUrl, user.getLastSyncedAvatarUrl());
                    updated++;
                }
            } catch (RuntimeException e) {
                log.warn("avatar regen failed for {}: {}", user.getId(), e.getMessage());
            }
            throttle();
        }
        return updated;
    }

    @Transactional
    public int regenerateCoversStaticVariants() {
        int updated = 0;
        for (Map map : mapRepository.findByActiveTrue()) {
            String key = map.getId().toString();
            if (!mediaProcessingService.avifExists(MAP_COVER_SUBDIR, key)) continue;
            try {
                String newUrl = mediaProcessingService.regenerateStaticVariant(MAP_COVER_SUBDIR, key);
                if (newUrl != null && !newUrl.equals(map.getCoverUrl())) {
                    map.setCoverUrl(newUrl);
                    mapRepository.save(map);
                    updated++;
                }
            } catch (RuntimeException e) {
                log.warn("cover regen failed for {}: {}", map.getId(), e.getMessage());
            }
            throttle();
        }
        return updated;
    }

    @Transactional
    public int regenerateCampaignBackgroundsStaticVariants() {
        int updated = 0;
        for (Campaign c : campaignRepository.findByActiveTrue()) {
            String key = c.getId().toString();
            if (c.getBackgroundUrl() == null || !mediaProcessingService.avifExists(CAMPAIGN_BG_SUBDIR, key)) continue;
            try {
                String newUrl = mediaProcessingService.regenerateStaticVariant(CAMPAIGN_BG_SUBDIR, key);
                if (newUrl != null && !newUrl.equals(c.getBackgroundUrl())) {
                    c.setBackgroundUrl(newUrl);
                    campaignRepository.save(c);
                    updated++;
                }
            } catch (RuntimeException e) {
                log.warn("campaign-bg regen failed for {}: {}", c.getId(), e.getMessage());
            }
            throttle();
        }
        return updated;
    }

    @Transactional
    public int regenerateCampaignIconsStaticVariants() {
        int updated = 0;
        for (Campaign c : campaignRepository.findByActiveTrue()) {
            String key = c.getId().toString();
            if (c.getIconUrl() == null || !mediaProcessingService.avifExists(CAMPAIGN_ICON_SUBDIR, key)) continue;
            try {
                String newUrl = mediaProcessingService.regenerateStaticVariant(CAMPAIGN_ICON_SUBDIR, key);
                if (newUrl != null && !newUrl.equals(c.getIconUrl())) {
                    c.setIconUrl(newUrl);
                    campaignRepository.save(c);
                    updated++;
                }
            } catch (RuntimeException e) {
                log.warn("campaign-icon regen failed for {}: {}", c.getId(), e.getMessage());
            }
            throttle();
        }
        return updated;
    }

    @Transactional
    public int regenerateItemIconsStaticVariants() {
        int updated = 0;
        for (Item item : itemRepository.findByActiveTrue()) {
            String key = item.getId().toString();
            if (item.getIconUrl() == null || !mediaProcessingService.avifExists(ITEM_ICON_SUBDIR, key)) continue;
            try {
                String newUrl = mediaProcessingService.regenerateStaticVariant(ITEM_ICON_SUBDIR, key);
                if (newUrl != null && !newUrl.equals(item.getIconUrl())) {
                    item.setIconUrl(newUrl);
                    itemRepository.save(item);
                    updated++;
                }
            } catch (RuntimeException e) {
                log.warn("item-icon regen failed for {}: {}", item.getId(), e.getMessage());
            }
            throttle();
        }
        return updated;
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
