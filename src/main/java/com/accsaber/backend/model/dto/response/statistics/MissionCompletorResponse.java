package com.accsaber.backend.model.dto.response.statistics;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MissionCompletorResponse {

    @JsonUnwrapped
    private UserIdentity user;
    private long missionsCompleted;
    private double missionXp;
}
