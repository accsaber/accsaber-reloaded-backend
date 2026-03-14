package com.accsaber.backend.service.infra;

import java.util.UUID;

import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import com.accsaber.backend.config.CacheConfig;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CacheService {

    private final CacheManager cacheManager;

    public void evictLeaderboard(UUID categoryId) {
        var cache = cacheManager.getCache(CacheConfig.LEADERBOARD_CACHE);
        if (cache != null) {
            cache.evict(categoryId.toString());
        }
    }
}
