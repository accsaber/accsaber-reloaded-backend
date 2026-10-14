package com.accsaber.backend.scheduler;

import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.PlayerImportService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlayerRefreshScheduler {

    private final UserRepository userRepository;
    private final PlayerImportService playerImportService;

    @Scheduled(cron = "${accsaber.scheduler.player-refresh-cron:0 0 4 * * *}")
    public void refreshAllPlayers() {
        List<Long> userIds = userRepository.findActiveIds();
        log.info("Starting scheduled player refresh for {} users", userIds.size());

        int success = 0;
        int failed = 0;
        for (Long userId : userIds) {
            try {
                playerImportService.refreshPlayerProfile(userId);
                success++;
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Player refresh interrupted");
                return;
            } catch (Exception e) {
                failed++;
                log.error("Failed to refresh player {}: {}", userId, e.getMessage());
            }
        }
        log.info("Player refresh complete: {} success, {} failed", success, failed);
    }

    @Async("taskExecutor")
    public void refreshAllPlayersAsync() {
        refreshAllPlayers();
    }
}
