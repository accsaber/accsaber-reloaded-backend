package com.accsaber.backend.model.dto.response.milestone;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MilestoneHolderResponse {

    private Long userId;
    private String name;
    private String avatarUrl;
    private String country;
    private Instant completedAt;
}
