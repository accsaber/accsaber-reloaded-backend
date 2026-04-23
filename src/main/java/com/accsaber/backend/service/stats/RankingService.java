package com.accsaber.backend.service.stats;

import java.math.BigDecimal;
import java.time.Instant;
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

import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserCategoryStatistics;
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

    public void updateRankingForUserAsync(UUID categoryId, Long userId) {
        updateRankingForUserAsync(categoryId, userId, null);
    }

    public void updateRankingForUserAsync(UUID categoryId, Long userId, Runnable postRankingCallback) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rankingExecutor.execute(() -> updateRankingForUser(categoryId, userId, postRankingCallback));
                }
            });
        } else {
            rankingExecutor.execute(() -> updateRankingForUser(categoryId, userId, postRankingCallback));
        }
    }

    @CacheEvict(value = "leaderboards", allEntries = true)
    public void updateRankingForUser(UUID categoryId, Long userId, Runnable postRankingCallback) {
        ReentrantLock lock = categoryLocks.computeIfAbsent(categoryId, k -> new ReentrantLock());
        lock.lock();
        try {
            transactionTemplate.executeWithoutResult(status -> applyIncrementalRanking(categoryId, userId));
        } catch (Exception e) {
            log.error("Incremental ranking failed for user {} in category {}: {}",
                    userId, categoryId, e.getMessage());
        } finally {
            lock.unlock();
        }
        if (postRankingCallback != null) {
            try {
                postRankingCallback.run();
            } catch (Exception e) {
                log.error("Error in post-ranking callback for user {} in category {}: {}",
                        userId, categoryId, e.getMessage());
            }
        }
    }

    private void applyIncrementalRanking(UUID categoryId, Long userId) {
        UserCategoryStatistics stats = statisticsRepository
                .findByUser_IdAndCategory_IdAndActiveTrue(userId, categoryId)
                .orElse(null);
        if (stats == null)
            return;
        User user = stats.getUser();
        if (!user.isActive() || user.isBanned())
            return;

        BigDecimal ap = stats.getAp();
        Instant tieBreaker = stats.getTopPlay() != null ? stats.getTopPlay().getTimeSet() : null;

        Integer oldRank = stats.getRanking();
        int newRank = (int) statisticsRepository.countActiveAheadInCategory(
                categoryId, userId, ap, tieBreaker) + 1;

        applyGlobalShift(categoryId, userId, oldRank, newRank);

        Integer newCountryRank = null;
        String country = user.getCountry();
        if (country != null) {
            Integer oldCountryRank = stats.getCountryRanking();
            newCountryRank = (int) statisticsRepository.countActiveAheadInCountry(
                    categoryId, userId, country, ap, tieBreaker) + 1;
            applyCountryShift(categoryId, userId, country, oldCountryRank, newCountryRank);
        }

        statisticsRepository.updateUserRankings(userId, categoryId, newRank, newCountryRank);
    }

    private void applyGlobalShift(UUID categoryId, Long userId, Integer oldRank, int newRank) {
        if (oldRank == null) {
            statisticsRepository.shiftGlobalRankingsDown(categoryId, userId, newRank, Integer.MAX_VALUE);
        } else if (newRank < oldRank) {
            statisticsRepository.shiftGlobalRankingsDown(categoryId, userId, newRank, oldRank);
        } else if (newRank > oldRank) {
            statisticsRepository.shiftGlobalRankingsUp(categoryId, userId, oldRank, newRank);
        }
    }

    private void applyCountryShift(UUID categoryId, Long userId, String country, Integer oldRank, int newRank) {
        if (oldRank == null) {
            statisticsRepository.shiftCountryRankingsDown(categoryId, userId, country, newRank, Integer.MAX_VALUE);
        } else if (newRank < oldRank) {
            statisticsRepository.shiftCountryRankingsDown(categoryId, userId, country, newRank, oldRank);
        } else if (newRank > oldRank) {
            statisticsRepository.shiftCountryRankingsUp(categoryId, userId, country, oldRank, newRank);
        }
    }
}
