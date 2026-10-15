package com.accsaber.backend.model.dto.response.statistics;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.MapDifficultyRef;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MapAvgApResponse {

    private MapDifficultyRef map;
    private Double averageWeightedAp;
    private long scoreCount;
    private UUID latestScoreId;
    private Instant latestScoreTimeSet;
}
