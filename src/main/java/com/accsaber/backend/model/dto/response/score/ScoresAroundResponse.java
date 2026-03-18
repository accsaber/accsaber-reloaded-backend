package com.accsaber.backend.model.dto.response.score;

import java.util.List;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ScoresAroundResponse {

    List<ScoreResponse> scoresAbove;
    ScoreResponse playerScore;
    List<ScoreResponse> scoresBelow;
}
