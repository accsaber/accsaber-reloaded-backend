package com.accsaber.backend.model.dto.response.map;

import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.entity.map.MapDifficultyStatus;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LeaderboardPreviewResponse {
    UUID mapDifficultyId;
    String songName;
    String difficulty;
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
        String userId;
        String name;
        String avatarUrl;
        String cdnAvatarUrl;
        String country;
        String platform;
        double accuracy;
        double ap;
        String modifiers;
    }
}
