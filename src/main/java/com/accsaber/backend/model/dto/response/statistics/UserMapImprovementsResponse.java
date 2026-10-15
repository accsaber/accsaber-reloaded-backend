package com.accsaber.backend.model.dto.response.statistics;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.MapDifficultyRef;
import com.accsaber.backend.model.dto.response.common.PlayerRef;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserMapImprovementsResponse {

    private PlayerRef player;
    private MapDifficultyRef map;
    private long improvementCount;
    private UUID latestScoreId;
    private Instant latestScoreTimeSet;
}
