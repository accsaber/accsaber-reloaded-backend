package com.accsaber.backend.scheduler;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.model.entity.clan.ClanXpSource;
import com.accsaber.backend.repository.clan.ClanXpGrantRepository;
import com.accsaber.backend.service.clan.ClanLevelService;
import com.accsaber.backend.service.clan.ClanXpAward;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClanPlayXpScheduler {

    private static final int CATCH_UP_DAYS = 7;

    private final ClanXpGrantRepository grantRepository;
    private final ClanLevelService levelService;
    private final ClanProperties clanProperties;

    @Scheduled(cron = "${accsaber.scheduler.clan-play-xp-cron:0 10 0 * * *}", zone = "UTC")
    public void grantDailyPlayXp() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int daysBack = CATCH_UP_DAYS; daysBack >= 1; daysBack--) {
            grantDay(today.minusDays(daysBack));
        }
    }

    int grantDay(LocalDate day) {
        Instant from = day.atStartOfDay(ZoneOffset.UTC).toInstant();
        String sourceId = day.toString();
        int granted = 0;
        for (ClanXpGrantRepository.MemberPlayXpView row : grantRepository.sumUngrantedMemberPlayXp(from,
                from.plus(1, ChronoUnit.DAYS), sourceId)) {
            try {
                ClanXpAward award = new ClanXpAward(row.getXp() * clanProperties.getPlayXpShare(),
                        ClanXpSource.daily_play, sourceId, true);
                if (levelService.grantXp(row.getClanId(), award)) {
                    granted++;
                }
            } catch (RuntimeException e) {
                log.warn("Daily play XP for clan {} on {} failed: {}", row.getClanId(), day, e.getMessage());
            }
        }
        if (granted > 0) {
            log.info("Granted daily play XP to {} clans for {}", granted, day);
        }
        return granted;
    }
}
