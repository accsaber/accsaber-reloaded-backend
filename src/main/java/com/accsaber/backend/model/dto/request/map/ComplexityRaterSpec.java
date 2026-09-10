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
        private int minScores = 100;

        @Min(1)
        private int fullScores = 200;

        @Min(1)
        private int minPlayers = 30;

        @Min(1)
        private int minPlayerPlays = 20;

        @DecimalMin("0.0")
        private double maxNudge = 0.5;
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

        private double njsSlope;

        private double boardSlope;
    }
}
