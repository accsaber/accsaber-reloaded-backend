package com.accsaber.backend.model.dto.response.statistics;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.PlayerRef;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserImprovementsResponse {

    private PlayerRef player;
    private long improvementCount;
    private UUID latestScoreId;
    private Instant latestScoreTimeSet;
}
