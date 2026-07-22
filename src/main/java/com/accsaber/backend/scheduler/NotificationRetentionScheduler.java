package com.accsaber.backend.scheduler;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.repository.notification.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationRetentionScheduler {

    private final NotificationRepository notificationRepository;

    @Value("${accsaber.notifications.retain-read-days:30}")
    private int retainReadDays;

    @Value("${accsaber.notifications.retain-days:120}")
    private int retainDays;

    @Scheduled(cron = "${accsaber.scheduler.notification-retention-cron:0 15 4 * * *}")
    @Transactional
    public void purgeOldNotifications() {
        Instant now = Instant.now();
        int read = notificationRepository.deleteReadOlderThan(now.minus(retainReadDays, ChronoUnit.DAYS));
        int stale = notificationRepository.deleteOlderThan(now.minus(retainDays, ChronoUnit.DAYS));
        if (read + stale > 0) {
            log.info("Purged {} read and {} stale notifications", read, stale);
        }
    }
}
