package com.accsaber.backend.model.dto.response.admin;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.accsaber.backend.model.complexity.ComplexityScenario;
import com.accsaber.backend.model.dto.request.map.ComplexityRaterSpec;
import com.accsaber.backend.model.dto.response.common.MapDifficultyRef;
import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;

import lombok.Builder;
import lombok.Value;

public final class ComplexityComparisonResponse {

    private ComplexityComparisonResponse() {
    }

    @Value
    @Builder
    public static class DifficultyRow {
        MapDifficultyRef map;
        String characteristic;
        String categoryCode;
        MapDifficultyStatus status;
        boolean complexityPinned;
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
        Double averageWeightedAp;
    }

    @Value
    @Builder
    public static class DifficultyPage {
        RoundSummary summary;
        List<DifficultyRow> rows;
        int page;
        int size;
        int total;
        int totalPages;
    }

    @Value
    @Builder
    public static class RoundSummary {
        int difficulties;
        int pinned;
        int missingEstimate;
        int staleEstimate;
        String modelHash;
        String scriptVersion;
        Instant estimatedAt;
        Map<ComplexityScenario, Integer> moving;
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
        int page;
        int size;
        int total;
        int totalPages;
    }

    @Value
    @Builder
    public static class ScoreRow {
        PlayerRef player;
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
        PlayerRef player;
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
        DifficultyPage difficulties;
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
        PlayerRef player;
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
