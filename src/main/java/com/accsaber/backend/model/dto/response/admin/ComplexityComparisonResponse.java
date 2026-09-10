package com.accsaber.backend.model.dto.response.admin;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.accsaber.backend.model.dto.request.map.ComplexityRaterSpec;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.service.map.ComplexityScenario;

import lombok.Builder;
import lombok.Value;

public final class ComplexityComparisonResponse {

    private ComplexityComparisonResponse() {
    }

    @Value
    @Builder
    public static class DifficultyRow {
        UUID mapDifficultyId;
        UUID mapId;
        String songName;
        String songSubName;
        String songAuthor;
        String mapAuthor;
        String coverUrl;
        String cdnCoverUrl;
        String difficulty;
        String characteristic;
        UUID categoryId;
        String categoryCode;
        MapDifficultyStatus status;
        int scores;
        Map<ComplexityScenario, MapValues> scenarios;
        Map<ComplexityScenario, MapValues> deltas;
        Map<ComplexityScenario, EstimateInfo> estimates;
    }

    @Value
    @Builder
    public static class MapValues {
        Double complexity;
        Double topAp;
        Double averageAp;
        Double averageWeightedAp;
    }

    @Value
    @Builder
    public static class EstimateInfo {
        String version;
        Instant updatedAt;
        Map<String, Object> inputs;
    }

    @Value
    @Builder
    public static class MapLeaderboard {
        DifficultyRow difficulty;
        List<ScoreRow> rows;
    }

    @Value
    @Builder
    public static class ScoreRow {
        String userId;
        String name;
        String avatarUrl;
        String cdnAvatarUrl;
        String country;
        double accuracy;
        Map<ComplexityScenario, PlayValues> scenarios;
        Map<ComplexityScenario, PlayValues> deltas;
    }

    @Value
    @Builder
    public static class PlayValues {
        Double ap;
        Double weightedAp;
        Integer position;
        Integer rank;
    }

    @Value
    @Builder
    public static class PlayerPlays {
        String userId;
        String name;
        String avatarUrl;
        String cdnAvatarUrl;
        String country;
        List<CategoryPlays> categories;
    }

    @Value
    @Builder
    public static class CategoryPlays {
        UUID categoryId;
        String categoryCode;
        Map<ComplexityScenario, TotalValues> scenarios;
        Map<ComplexityScenario, TotalValues> deltas;
        List<PlayRow> plays;
    }

    @Value
    @Builder
    public static class PlayRow {
        DifficultyRow difficulty;
        double accuracy;
        Map<ComplexityScenario, PlayValues> scenarios;
        Map<ComplexityScenario, PlayValues> deltas;
    }

    @Value
    @Builder
    public static class Rater {
        String version;
        List<Double> worstBands;
        ComplexityRaterSpec rater;
    }

    @Value
    @Builder
    public static class Preview {
        ComplexityRaterSpec rater;
        List<DifficultyRow> difficulties;
        PlayerBoard players;
    }

    @Value
    @Builder
    public static class PlayerBoard {
        UUID categoryId;
        String categoryCode;
        Map<ComplexityScenario, LadderValues> ladders;
        List<PlayerRow> rows;
    }

    @Value
    @Builder
    public static class LadderValues {
        int players;
        double totalAp;
        int playersWith900;
        int playersWith1000;
        int playersWith1100;
        int playsWith1000;
        int playsWith1100;
        double topPlayAp;
    }

    @Value
    @Builder
    public static class PlayerRow {
        String userId;
        String name;
        String avatarUrl;
        String cdnAvatarUrl;
        String country;
        Map<ComplexityScenario, TotalValues> scenarios;
        Map<ComplexityScenario, TotalValues> deltas;
    }

    @Value
    @Builder
    public static class TotalValues {
        Double ap;
        Integer rank;
    }
}
