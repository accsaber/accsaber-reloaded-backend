package com.accsaber.backend.model.dto.response.statistics;

import java.time.Instant;

import com.accsaber.backend.model.dto.response.common.PlayerRef;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EventMissionLeaderboardResponse {

    private long rank;
    private PlayerRef player;
    private long completions;
    private long xpEarned;
    private long itemsAwarded;
    private Instant lastCompletedAt;
}
