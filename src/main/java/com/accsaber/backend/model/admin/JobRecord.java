package com.accsaber.backend.model.admin;

import java.time.Instant;
import java.util.UUID;

public record JobRecord(
        UUID id,
        JobType type,
        String detail,
        JobStatus status,
        Instant startedAt,
        Instant finishedAt,
        String error) {
}
