package com.accsaber.backend.scheduler;

import java.time.Instant;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.accsaber.backend.repository.clan.ClanSeasonRepository;
import com.accsaber.backend.service.clan.ClanSeasonService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClanSeasonScheduler {

    private final ClanSeasonRepository seasonRepository;
    private final ClanSeasonService seasonService;

    @Scheduled(fixedDelay = 300_000, initialDelay = 30_000)
    public void rollSeasons() {
        for (UUID seasonId : seasonRepository.findEndedUnclosedIds(Instant.now())) {
            try {
                seasonService.close(seasonId);
                log.info("Closed clan season {}", seasonId);
            } catch (RuntimeException e) {
                log.error("Closing clan season {} failed: {}", seasonId, e.getMessage(), e);
                return;
            }
        }
        seasonService.ensureCurrent();
    }
}
