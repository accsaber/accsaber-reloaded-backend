package com.accsaber.backend.model.dto.response.admin;

import java.util.List;

import com.accsaber.backend.model.admin.JobGroup;
import com.accsaber.backend.model.admin.JobType;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class JobTypeResponse {

    JobType type;
    JobGroup group;
    String label;
    String description;
    List<JobFieldResponse> fields;

    public static JobTypeResponse from(JobType type) {
        return JobTypeResponse.builder()
                .type(type)
                .group(type.getGroup())
                .label(type.getLabel())
                .description(type.getDescription())
                .fields(type.getFields().stream().map(JobFieldResponse::from).toList())
                .build();
    }
}
