package com.accsaber.backend.scheduler;

import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.accsaber.backend.service.clan.ClanMissionAssignmentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClanMissionScheduler {

    private final ClanMissionAssignmentService assignmentService;

    @Scheduled(fixedDelay = 600_000, initialDelay = 120_000)
    public void refill() {
        assignmentService.expireStale();
        int opened = 0;
        for (UUID clanId : assignmentService.clansWithoutMissions()) {
            try {
                opened += assignmentService.fill(clanId);
            } catch (Exception e) {
                log.warn("Clan mission fill failed for clan {}: {}", clanId, e.getMessage());
            }
        }
        if (opened > 0) {
            log.info("Opened {} clan missions", opened);
        }
    }
}
