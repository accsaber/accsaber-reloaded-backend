package com.accsaber.backend.model.dto.response.player;

import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PinnedScoreResponse {

    ScoreResponse score;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String comment;
}
