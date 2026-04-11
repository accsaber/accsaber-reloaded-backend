package com.accsaber.backend.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.repository.user.UserCategoryRankingHistoryRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.repository.user.UserXpRankingHistoryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class XpRankingScheduler {

    private final UserRepository userRepository;
    private final UserXpRankingHistoryRepository xpRankingHistoryRepository;
    private final UserCategoryRankingHistoryRepository categoryRankingHistoryRepository;

    @Scheduled(fixedRate = 300_000)
    public void refreshXpRankings() {
        log.debug("Refreshing XP rankings");
        try {
            userRepository.assignXpRankings();
            userRepository.assignXpCountryRankings();
        } catch (Exception e) {
            log.error("Failed to refresh XP rankings: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void snapshotXpRankings() {
        log.debug("Snapshotting XP rankings");
        try {
            xpRankingHistoryRepository.snapshotChangedRankings();
        } catch (Exception e) {
            log.error("Failed to snapshot XP rankings: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void snapshotCategoryRankings() {
        log.debug("Snapshotting category rankings");
        try {
            categoryRankingHistoryRepository.snapshotChangedRankings();
        } catch (Exception e) {
            log.error("Failed to snapshot category rankings: {}", e.getMessage(), e);
        }
    }
}
