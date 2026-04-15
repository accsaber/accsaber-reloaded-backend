package com.accsaber.backend.service.stats;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RankingService {

    private final UserCategoryStatisticsRepository statisticsRepository;
    private final TransactionTemplate transactionTemplate;
    private final Executor rankingExecutor;

    private final ConcurrentHashMap<UUID, ReentrantLock> categoryLocks = new ConcurrentHashMap<>();

    private static final int IDLE = 0;
    private static final int RUNNING = 1;
    private static final int DIRTY = 2;

    private final ConcurrentHashMap<UUID, AtomicInteger> categoryState = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ConcurrentLinkedQueue<Runnable>> pendingCallbacks = new ConcurrentHashMap<>();

    public RankingService(
            UserCategoryStatisticsRepository statisticsRepository,
            TransactionTemplate transactionTemplate,
            @Qualifier("rankingExecutor") Executor rankingExecutor) {
        this.statisticsRepository = statisticsRepository;
        this.transactionTemplate = transactionTemplate;
        this.rankingExecutor = rankingExecutor;
    }

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

    public void updateRankingsAsync(UUID categoryId) {
        updateRankingsAsync(categoryId, null);
    }

    public void updateRankingsAsync(UUID categoryId, Runnable postRankingCallback) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchRankingUpdate(categoryId, postRankingCallback);
                }
            });
        } else {
            dispatchRankingUpdate(categoryId, postRankingCallback);
        }
    }

    private void dispatchRankingUpdate(UUID categoryId, Runnable postRankingCallback) {
        if (postRankingCallback != null) {
            pendingCallbacks.computeIfAbsent(categoryId, k -> new ConcurrentLinkedQueue<>())
                    .add(postRankingCallback);
        }

        AtomicInteger state = categoryState.computeIfAbsent(categoryId, k -> new AtomicInteger(IDLE));

        if (state.compareAndSet(IDLE, RUNNING)) {
            rankingExecutor.execute(() -> processCoalesced(categoryId, state));
        } else {
            state.set(DIRTY);
        }
    }

    private void processCoalesced(UUID categoryId, AtomicInteger state) {
        try {
            do {
                state.set(RUNNING);
                updateRankings(categoryId);
                drainCallbacks(categoryId);
            } while (!state.compareAndSet(RUNNING, IDLE));
        } catch (Exception e) {
            state.set(IDLE);
            log.error("Error during ranking update for category {}: {}", categoryId, e.getMessage());
        }
    }

    private void drainCallbacks(UUID categoryId) {
        ConcurrentLinkedQueue<Runnable> callbacks = pendingCallbacks.get(categoryId);
        if (callbacks == null)
            return;
        Runnable cb;
        while ((cb = callbacks.poll()) != null) {
            try {
                cb.run();
            } catch (Exception e) {
                log.error("Error in post-ranking callback for category {}: {}", categoryId, e.getMessage());
            }
        }
    }
}
