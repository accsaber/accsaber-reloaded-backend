package com.accsaber.backend.model.dto.response.statistics;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserImprovementsResponse {

    @JsonUnwrapped
    private UserIdentity user;
    private long improvementCount;
    private UUID latestScoreId;
    private Instant latestScoreTimeSet;
}
