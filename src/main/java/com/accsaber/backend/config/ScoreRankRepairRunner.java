package com.accsaber.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.accsaber.backend.service.score.ScoreRankingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class ScoreRankRepairRunner implements ApplicationRunner {

    private final ScoreRankingService scoreRankingService;

    @Value("${accsaber.backfill.on-startup:true}")
    private boolean backfillOnStartup;

    @Override
    public void run(ApplicationArguments args) {
        if (backfillOnStartup) {
            log.info("Rank repair skipped — backfill runner will trigger it after import");
            return;
        }
        scoreRankingService.reassignAllRanks();
    }
}
