package com.accsaber.backend.model.dto.response.milestone;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PrerequisiteLinkResponse {

    private UUID id;
    private UUID milestoneId;
    private UUID prerequisiteMilestoneId;
    private String prerequisiteTitle;
    private String prerequisiteTier;
    private boolean blocker;
    private Instant createdAt;
}
