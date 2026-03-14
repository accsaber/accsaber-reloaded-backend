package com.accsaber.backend.runner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.accsaber.backend.service.score.ScoreImportService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupBackfillRunner implements ApplicationRunner {

    private final ScoreImportService scoreImportService;

    @Value("${accsaber.backfill.on-startup:true}")
    private boolean backfillOnStartup;

    @Override
    public void run(ApplicationArguments args) {
        if (!backfillOnStartup) {
            log.info("Startup backfill disabled");
            return;
        }
        log.info("Starting backfill of all ranked difficulties");
        scoreImportService.backfillAllRankedDifficulties();
    }
}
