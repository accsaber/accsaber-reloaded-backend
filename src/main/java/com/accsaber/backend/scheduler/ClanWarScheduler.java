package com.accsaber.backend.scheduler;

import java.time.Duration;
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
import com.accsaber.backend.service.clan.war.ClanWarCombatService;
import com.accsaber.backend.service.clan.war.ClanWarPoolService;
import com.accsaber.backend.service.clan.war.ClanWarRosterService;
import com.accsaber.backend.service.clan.war.ClanWarService;
import com.accsaber.backend.service.clan.war.ClanWarSettlementService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClanWarScheduler {

    private static final int SETTLEMENT_BATCH = 20;
    private static final int MISSED_PLAY_BATCH = 500;
    private static final Duration MISSED_PLAY_LOOKBACK = Duration.ofHours(48);
    private static final Duration MISSED_PLAY_SETTLE = Duration.ofMinutes(10);

    private final ClanWarRepository warRepository;
    private final ClanWarCombatService combatService;
    private final ClanWarPoolService poolService;
    private final ClanWarRosterService rosterService;
    private final ClanWarService warService;
    private final ClanWarSettlementService settlementService;
    private final ClanProperties clanProperties;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void advance() {
        Instant now = Instant.now();
        each(warRepository.findPicksDue(now), poolService::closePicks);
        each(warRepository.findStartsDue(now), rosterService::start);
        Instant quietCutoff = now.minus(clanProperties.getWar().getDrawAfterQuietDays(), ChronoUnit.DAYS);
        each(warRepository.findQuietSince(quietCutoff), warId -> warService.end(warId, ClanWarOutcome.drawn));
        each(warRepository.findActiveStartedBefore(now.minus(clanProperties.getWar().getMaxDuration())),
                warService::endAtCap);
        each(warRepository.findEndedUnsettled(SETTLEMENT_BATCH), settlementService::settle);
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    public void scoreMissedPlays() {
        Instant now = Instant.now();
        int scored = combatService.scoreMissed(now.minus(MISSED_PLAY_LOOKBACK), now.minus(MISSED_PLAY_SETTLE),
                MISSED_PLAY_BATCH);
        if (scored > 0) {
            log.info("Caught up {} missed clan war plays", scored);
        }
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
