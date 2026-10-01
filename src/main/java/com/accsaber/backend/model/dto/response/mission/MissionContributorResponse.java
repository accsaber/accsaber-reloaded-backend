package com.accsaber.backend.model.dto.response.mission;

import java.time.Instant;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.mission.MissionContribution;
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

    public static MissionContributorResponse from(MissionContribution c, long rank) {
        return MissionContributorResponse.builder()
                .rank(rank)
                .player(PlayerRef.of(c.getUser()))
                .contribution(c.getContribution())
                .firstAt(c.getFirstAt())
                .lastAt(c.getLastAt())
                .rewardedAt(c.getRewardedAt())
                .build();
    }
}
