package com.accsaber.backend.model.dto.response.statistics;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EventMissionLeaderboardResponse {

    private long rank;
    @JsonUnwrapped
    private UserIdentity user;
    private long completions;
    private long xpEarned;
    private long itemsAwarded;
    private Instant lastCompletedAt;
}
