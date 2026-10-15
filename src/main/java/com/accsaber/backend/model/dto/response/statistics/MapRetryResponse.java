package com.accsaber.backend.model.dto.response.statistics;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.MapDifficultyRef;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MapRetryResponse {

    private MapDifficultyRef map;
    private long supersededCount;
    private UUID latestScoreId;
    private Instant latestScoreTimeSet;
}
