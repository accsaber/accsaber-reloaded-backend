package com.accsaber.backend.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.accsaber.backend.service.item.LevelRewardService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LevelRewardScheduler {

    private final LevelRewardService levelRewardService;

    @Scheduled(fixedRate = 300_000, initialDelay = 60_000)
    public void grantMissingLevelRewards() {
        levelRewardService.grantMissingLevelRewards();
    }
}
