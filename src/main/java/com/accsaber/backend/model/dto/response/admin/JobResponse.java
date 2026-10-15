package com.accsaber.backend.model.dto.response.admin;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.admin.JobRecord;
import com.accsaber.backend.model.admin.JobStatus;
import com.accsaber.backend.model.admin.JobType;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class JobResponse {

    UUID id;
    JobType type;
    String detail;
    JobStatus status;
    Instant startedAt;
    Instant finishedAt;
    String error;

    public static JobResponse from(JobRecord job) {
        return JobResponse.builder()
                .id(job.id())
                .type(job.type())
                .detail(job.detail())
                .status(job.status())
                .startedAt(job.startedAt())
                .finishedAt(job.finishedAt())
                .error(job.error())
                .build();
    }
}
