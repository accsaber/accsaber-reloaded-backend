package com.accsaber.backend.service.stats;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RankingService {

    private final UserCategoryStatisticsRepository statisticsRepository;
    private final TransactionTemplate transactionTemplate;

    private final ConcurrentHashMap<UUID, ReentrantLock> categoryLocks = new ConcurrentHashMap<>();

    @CacheEvict(value = "leaderboards", allEntries = true)
    public void updateRankings(UUID categoryId) {
        ReentrantLock lock = categoryLocks.computeIfAbsent(categoryId, k -> new ReentrantLock());
        lock.lock();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                statisticsRepository.assignGlobalRankings(categoryId);
                statisticsRepository.assignCountryRankings(categoryId);
            });
        } finally {
            lock.unlock();
        }
    }

    @Async("rankingExecutor")
    public void updateRankingsAsync(UUID categoryId) {
        updateRankings(categoryId);
    }

    @Async("rankingExecutor")
    public void updateRankingsAsync(UUID categoryId, Runnable postRankingCallback) {
        updateRankings(categoryId);
        if (postRankingCallback != null) {
            postRankingCallback.run();
        }
    }
}
