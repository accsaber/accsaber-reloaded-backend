package com.accsaber.backend.runner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.accsaber.backend.service.score.ScoreGapFillService;
import com.accsaber.backend.service.score.ScoreImportService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class StartupBackfillRunner implements ApplicationRunner {

    private final ScoreImportService scoreImportService;
    private final ScoreGapFillService scoreGapFillService;

    @Value("${accsaber.backfill.on-startup:true}")
    private boolean backfillOnStartup;

    @Value("${accsaber.backfill.startup-mode:gap-fill}")
    private String startupMode;

    @Value("${accsaber.backfill.gap-fill-days:7}")
    private int gapFillDays;

    @Value("${accsaber.backfill.startup-delay-seconds:0}")
    private int startupDelaySeconds;

    @Override
    public void run(ApplicationArguments args) {
        if (!backfillOnStartup) {
            log.info("Startup backfill disabled");
            return;
        }
        if (startupDelaySeconds <= 0) {
            trigger();
            return;
        }
        log.info("Startup backfill scheduled in {}s", startupDelaySeconds);
        Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "startup-backfill");
            thread.setDaemon(true);
            return thread;
        }).schedule(this::trigger, startupDelaySeconds, TimeUnit.SECONDS);
    }

    private void trigger() {
        if ("full".equalsIgnoreCase(startupMode)) {
            log.info("Starting full backfill of all ranked difficulties");
            scoreImportService.backfillAllRankedDifficulties();
        } else {
            Instant since = Instant.now().minus(gapFillDays, ChronoUnit.DAYS);
            log.info("Starting startup gap-fill for last {} days (since {})", gapFillDays, since);
            scoreGapFillService.startupGapFillAllRankedDifficulties(since);
        }
    }
}
