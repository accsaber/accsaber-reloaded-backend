package com.accsaber.backend.model.dto.response.mission;

import java.time.Instant;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MissionContributorResponse {

    private long rank;
    private PlayerRef player;
    private double contribution;
    private Instant firstAt;
    private Instant lastAt;
    private Instant rewardedAt;
}
