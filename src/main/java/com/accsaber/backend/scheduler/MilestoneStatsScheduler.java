package com.accsaber.backend.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.accsaber.backend.service.milestone.MilestoneService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MilestoneStatsScheduler {

    private final MilestoneService milestoneService;

    @Scheduled(fixedRate = 300_000)
    public void refreshMilestoneCompletionStats() {
        log.debug("Refreshing milestone completion stats materialized view");
        try {
            milestoneService.refreshCompletionStats();
        } catch (Exception e) {
            log.error("Failed to refresh milestone completion stats: {}", e.getMessage(), e);
        }
    }
}
