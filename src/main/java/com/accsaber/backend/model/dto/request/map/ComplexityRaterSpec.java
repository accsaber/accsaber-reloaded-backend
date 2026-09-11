package com.accsaber.backend.model.dto.request.map;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ComplexityRaterSpec {

    @NotNull
    @DecimalMin("0.005")
    @DecimalMax("0.5")
    private Double worstShare;

    @NotNull
    @Valid
    private Board board = new Board();

    @NotEmpty
    @Valid
    private Map<String, Coefficients> categories = new LinkedHashMap<>();

    @Valid
    private Map<String, Coefficients> boardCategories = new LinkedHashMap<>();

    @Data
    public static class Board {

        @Min(0)
        private int minScores = 50;

        @Min(1)
        private int fullScores = 70;

        @Min(1)
        private int minPlayers = 10;

        @Min(1)
        private int minPlayerPlays = 20;

        @Min(1)
        private int topPlays = 10;

        @DecimalMin("0.0")
        private double maxNudge = 1.5;
    }

    @Data
    public static class Coefficients {

        @NotNull
        private Double intercept;

        @NotNull
        private Double meanSlope;

        @NotNull
        private Double worstSlope;

        private double resetSlope;

        private double dotSlope;

        private double notesSlope;

        private double npsSlope;

        private double njsSlope;

        private double boardSlope;
    }
}
