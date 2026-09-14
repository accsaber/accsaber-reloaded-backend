package com.accsaber.backend.scheduler;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.model.entity.clan.war.ClanWarOutcome;
import com.accsaber.backend.repository.clan.war.ClanWarRepository;
import com.accsaber.backend.service.clan.war.ClanWarPoolService;
import com.accsaber.backend.service.clan.war.ClanWarRosterService;
import com.accsaber.backend.service.clan.war.ClanWarService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClanWarScheduler {

    private final ClanWarRepository warRepository;
    private final ClanWarPoolService poolService;
    private final ClanWarRosterService rosterService;
    private final ClanWarService warService;
    private final ClanProperties clanProperties;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void advance() {
        Instant now = Instant.now();
        each(warRepository.findPicksDue(now), poolService::closePicks);
        each(warRepository.findStartsDue(now), rosterService::start);
        Instant quietCutoff = now.minus(clanProperties.getWar().getDrawAfterQuietDays(), ChronoUnit.DAYS);
        each(warRepository.findQuietSince(quietCutoff), warId -> warService.end(warId, ClanWarOutcome.drawn));
    }

    private void each(List<UUID> warIds, Consumer<UUID> step) {
        for (UUID warId : warIds) {
            try {
                step.accept(warId);
            } catch (Exception e) {
                log.warn("Clan war {} could not advance: {}", warId, e.getMessage());
            }
        }
    }
}
