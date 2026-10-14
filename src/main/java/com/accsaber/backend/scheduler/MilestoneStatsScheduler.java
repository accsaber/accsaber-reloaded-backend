package com.accsaber.backend.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.accsaber.backend.service.milestone.MilestoneProgressCalculator;
import com.accsaber.backend.service.milestone.MilestoneService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MilestoneStatsScheduler {

    private final MilestoneService milestoneService;
    private final MilestoneProgressCalculator progressCalculator;

    @Scheduled(fixedDelay = 900_000, initialDelay = 60_000)
    public void refreshMilestoneCompletionStats() {
        log.debug("Refreshing milestone completion stats materialized view");
        milestoneService.refreshCompletionStats();
        progressCalculator.evictPopulationCache();
    }
}
