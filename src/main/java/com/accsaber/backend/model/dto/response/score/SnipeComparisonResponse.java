package com.accsaber.backend.model.dto.response.score;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SnipeComparisonResponse {

    ScoreResponse sniperScore;
    ScoreResponse targetScore;
    int scoreDelta;
}
