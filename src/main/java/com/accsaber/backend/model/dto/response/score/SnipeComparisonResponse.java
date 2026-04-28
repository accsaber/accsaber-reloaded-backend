package com.accsaber.backend.model.dto.response.score;

import com.accsaber.backend.model.dto.response.map.PublicMapDifficultyResponse;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SnipeComparisonResponse {

    PublicMapDifficultyResponse mapDifficulty;
    ScoreResponse sniperScore;
    ScoreResponse targetScore;
    int scoreDelta;
}
