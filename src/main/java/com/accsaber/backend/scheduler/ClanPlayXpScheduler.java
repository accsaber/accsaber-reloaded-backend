package com.accsaber.backend.scheduler;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.accsaber.backend.service.clan.ClanPlayXpService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClanPlayXpScheduler {

    private static final int CATCH_UP_HOURS = 48;

    private final ClanPlayXpService playXpService;

    @Scheduled(cron = "${accsaber.scheduler.clan-play-xp-cron:0 7 * * * *}", zone = "UTC")
    public void grantMissedPlayXp() {
        Instant now = Instant.now();
        int granted = playXpService.grantMissed(now.minus(CATCH_UP_HOURS, ChronoUnit.HOURS), now);
        if (granted > 0) {
            log.info("Caught up clan XP for {} plays", granted);
        }
    }
}
