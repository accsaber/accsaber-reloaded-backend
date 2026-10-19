package com.accsaber.backend.service.infra;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import com.accsaber.backend.repository.clan.ClanSeasonRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;

@Service
@Getter
public class MetricsService {

        private static final Duration SEASON_CLOSE_GRACE = Duration.ofMinutes(15);

        private final Counter blScoresIngested;
        private final Counter ssScoresIngested;
        private final Counter outboundBeatLeader;
        private final Counter outboundScoreSaber;
        private final Counter outboundBeatSaver;
        private final Timer scoreProcessingTimer;

        public MetricsService(MeterRegistry registry,
                        Executor taskExecutor,
                        Executor rankingExecutor,
                        Executor backfillExecutor,
                        ClanSeasonRepository clanSeasonRepository) {

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

                scoreProcessingTimer = Timer.builder("accsaber.scores.processing.duration")
                                .description("Time to process a score submission end-to-end")
                                .register(registry);

                registerExecutorGauges(registry, "task", taskExecutor);
                registerExecutorGauges(registry, "ranking", rankingExecutor);
                registerExecutorGauges(registry, "backfill", backfillExecutor);

                registry.gauge("accsaber.clans.seasons.close.overdue", clanSeasonRepository,
                                repo -> repo.findEndedUnclosedIds(Instant.now().minus(SEASON_CLOSE_GRACE)).size());
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
