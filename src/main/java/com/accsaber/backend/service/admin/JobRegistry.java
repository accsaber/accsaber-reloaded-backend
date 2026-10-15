package com.accsaber.backend.service.admin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.stereotype.Component;

import com.accsaber.backend.model.admin.JobRecord;
import com.accsaber.backend.model.admin.JobStatus;
import com.accsaber.backend.model.admin.JobType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JobRegistry {

    private static final int RECENT_LIMIT = 50;

    private final Map<UUID, JobRecord> running = new ConcurrentHashMap<>();
    private final Deque<JobRecord> recent = new ConcurrentLinkedDeque<>();

    public JobRecord start(JobType type, String detail) {
        JobRecord job = new JobRecord(UUID.randomUUID(), type, detail, JobStatus.RUNNING,
                Instant.now(), null, null);
        running.put(job.id(), job);
        log.info("Job {} started: {} {}", job.id(), type, detail != null ? detail : "");
        return job;
    }

    public void succeed(UUID jobId) {
        finish(jobId, JobStatus.SUCCEEDED, null);
    }

    public void fail(UUID jobId, Throwable error) {
        String message = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
        finish(jobId, JobStatus.FAILED, message);
        log.error("Job {} failed: {}", jobId, message, error);
    }

    private void finish(UUID jobId, JobStatus status, String error) {
        JobRecord job = running.remove(jobId);
        if (job == null) {
            return;
        }
        recent.addFirst(new JobRecord(job.id(), job.type(), job.detail(), status,
                job.startedAt(), Instant.now(), error));
        while (recent.size() > RECENT_LIMIT) {
            recent.pollLast();
        }
    }

    public List<JobRecord> list() {
        List<JobRecord> all = new ArrayList<>(running.values());
        all.sort(Comparator.comparing(JobRecord::startedAt).reversed());
        all.addAll(recent);
        return all;
    }

    public Optional<JobRecord> find(UUID jobId) {
        JobRecord active = running.get(jobId);
        if (active != null) {
            return Optional.of(active);
        }
        return recent.stream().filter(job -> job.id().equals(jobId)).findFirst();
    }

    public boolean isRunning(JobType type) {
        return running.values().stream().anyMatch(job -> job.type() == type);
    }
}
