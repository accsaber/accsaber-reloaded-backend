package com.accsaber.backend.service.infra;

import java.util.concurrent.Executor;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;

@Service
@Getter
public class MetricsService {

        private final Counter scoresSubmitted;
        private final Counter scoresRejected;
        private final Counter blScoresIngested;
        private final Counter ssScoresIngested;
        private final Counter outboundBeatLeader;
        private final Counter outboundScoreSaber;
        private final Counter outboundBeatSaver;
        private final Counter outboundAiComplexity;
        private final Counter outboundFailures;
        private final Counter asyncTaskFailures;
        private final Timer scoreProcessingTimer;
        private final Timer apRecalculationTimer;

        public MetricsService(MeterRegistry registry,
                        Executor taskExecutor,
                        Executor rankingExecutor,
                        Executor backfillExecutor) {

                scoresSubmitted = Counter.builder("accsaber.scores.submitted")
                                .description("Total scores successfully submitted")
                                .register(registry);

                scoresRejected = Counter.builder("accsaber.scores.rejected")
                                .description("Scores rejected (banned mods, not ranked, worse score)")
                                .register(registry);

                blScoresIngested = Counter.builder("accsaber.scores.ingested")
                                .tag("platform", "beatleader")
                                .description("Scores ingested from BeatLeader")
                                .register(registry);

                ssScoresIngested = Counter.builder("accsaber.scores.ingested")
                                .tag("platform", "scoresaber")
                                .description("Scores ingested from ScoreSaber")
                                .register(registry);

                outboundBeatLeader = Counter.builder("accsaber.requests.outbound")
                                .tag("target", "beatleader")
                                .description("Outbound API calls to BeatLeader")
                                .register(registry);

                outboundScoreSaber = Counter.builder("accsaber.requests.outbound")
                                .tag("target", "scoresaber")
                                .description("Outbound API calls to ScoreSaber")
                                .register(registry);

                outboundBeatSaver = Counter.builder("accsaber.requests.outbound")
                                .tag("target", "beatsaver")
                                .description("Outbound API calls to BeatSaver")
                                .register(registry);

                outboundAiComplexity = Counter.builder("accsaber.requests.outbound")
                                .tag("target", "aicomplexity")
                                .description("Outbound API calls to AI Complexity service")
                                .register(registry);

                outboundFailures = Counter.builder("accsaber.requests.outbound.failures")
                                .description("Failed outbound API calls")
                                .register(registry);

                asyncTaskFailures = Counter.builder("accsaber.async.failures")
                                .description("Failed async task executions")
                                .register(registry);

                scoreProcessingTimer = Timer.builder("accsaber.scores.processing.duration")
                                .description("Time to process a score submission end-to-end")
                                .register(registry);

                apRecalculationTimer = Timer.builder("accsaber.ap.recalculation.duration")
                                .description("Time for full AP recalculation jobs")
                                .register(registry);

                registerExecutorGauges(registry, "task", taskExecutor);
                registerExecutorGauges(registry, "ranking", rankingExecutor);
                registerExecutorGauges(registry, "backfill", backfillExecutor);
        }

        private void registerExecutorGauges(MeterRegistry registry, String name, Executor executor) {
                if (executor instanceof ThreadPoolTaskExecutor pool) {
                        registry.gauge("accsaber.executor.queue.depth",
                                        io.micrometer.core.instrument.Tags.of("executor", name),
                                        pool, p -> p.getThreadPoolExecutor().getQueue().size());
                        registry.gauge("accsaber.executor.active",
                                        io.micrometer.core.instrument.Tags.of("executor", name),
                                        pool, p -> p.getThreadPoolExecutor().getActiveCount());
                }
        }
}
