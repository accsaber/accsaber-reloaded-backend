package com.accsaber.backend.config;

import org.slf4j.MDC;
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        return pool(4, 8, 100, "accsaber-async-");
    }

    @Bean(name = "rankingExecutor")
    public Executor rankingExecutor() {
        return pool(2, 4, 50, "accsaber-ranking-");
    }

    @Bean(name = "backfillExecutor")
    public Executor backfillExecutor() {
        return pool(10, 10, Integer.MAX_VALUE, "accsaber-backfill-");
    }

    @Bean(name = "cdnBackfillExecutor")
    public Executor cdnBackfillExecutor() {
        return pool(6, 6, Integer.MAX_VALUE, "accsaber-cdn-backfill-");
    }

    @Bean(name = "ingestionScheduler")
    public ScheduledExecutorService ingestionScheduler() {
        return Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "accsaber-ingestion");
            t.setDaemon(true);
            return t;
        });
    }

    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.build();
    }

    private ThreadPoolTaskExecutor pool(int core, int max, int queue, String prefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setThreadNamePrefix(prefix);
        executor.setTaskDecorator(mdcTaskDecorator());
        executor.initialize();
        return executor;
    }

    private TaskDecorator mdcTaskDecorator() {
        return runnable -> {
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            return () -> {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                try {
                    runnable.run();
                } finally {
                    MDC.clear();
                }
            };
        };
    }
}
