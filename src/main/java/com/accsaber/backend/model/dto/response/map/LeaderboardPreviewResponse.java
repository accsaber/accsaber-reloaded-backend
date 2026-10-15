package com.accsaber.backend.model.dto.response.map;

import java.util.List;

import com.accsaber.backend.model.dto.response.common.MapDifficultyRef;
import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LeaderboardPreviewResponse {
    MapDifficultyRef map;
    String characteristic;
    String categoryCode;
    MapDifficultyStatus status;
    Double complexity;
    String complexitySource;
    int fetched;
    List<Row> rows;

    @Value
    @Builder
    public static class Row {
        int rank;
        PlayerRef player;
        String platform;
        double accuracy;
        double ap;
        String modifiers;
    }
}
