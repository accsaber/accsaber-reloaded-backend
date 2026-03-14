package com.accsaber.backend.scheduler;

import java.time.Instant;
import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.service.player.PlayerImportService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlayerRefreshScheduler {

    private final ScoreRepository scoreRepository;
    private final PlayerImportService playerImportService;

    @Scheduled(cron = "${accsaber.scheduler.player-refresh-cron:0 0 4 * * *}")
    public void refreshAllPlayers() {
        Instant since = Instant.now().minusSeconds(24 * 60 * 60);
        List<User> users = scoreRepository.findDistinctUsersWithScoresSince(since);
        log.info("Starting scheduled player refresh for {} active users (scored in last 24h)", users.size());

        int success = 0;
        int failed = 0;
        for (User user : users) {
            try {
                playerImportService.refreshPlayerProfile(user.getId());
                success++;
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Player refresh interrupted");
                return;
            } catch (Exception e) {
                failed++;
                log.error("Failed to refresh player {}: {}", user.getId(), e.getMessage());
            }
        }
        log.info("Player refresh complete: {} success, {} failed", success, failed);
    }

    @Async("taskExecutor")
    public void refreshAllPlayersAsync() {
        refreshAllPlayers();
    }
}
