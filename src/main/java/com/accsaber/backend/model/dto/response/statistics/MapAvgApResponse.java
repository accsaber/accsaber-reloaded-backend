package com.accsaber.backend.model.dto.response.statistics;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MapAvgApResponse {

    @JsonUnwrapped
    private MapIdentity map;
    private Double averageWeightedAp;
    private long scoreCount;
    private UUID latestScoreId;
    private Instant latestScoreTimeSet;
}
