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
    @Valid
    private PayFair payFair = new PayFair();

    @NotEmpty
    @Valid
    private Map<String, Coefficients> categories = new LinkedHashMap<>();

    @Data
    public static class PayFair {

        @Min(1)
        private int minPlayerPlays = 20;

        @Min(1)
        private int topPlays = 10;

        @Min(1)
        private int usualPlays = 20;

        @Min(1)
        private int minPlays = 6;

        @Min(0)
        private int minScores = 50;

        @Min(1)
        private int fullScores = 70;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double maxWeight = 0.6;
    }

    @Data
    public static class Coefficients {

        @NotNull
        private Double intercept;

        @NotNull
        private Double meanSlope;

        private double resetSlope;

        private double dotSlope;

        private double bottomUpSlope;

        private double crossoverSlope;

        private double topRowSlope;

        private double diagonalSlope;

        private double verticalStackSlope;

        private double outerSlope;

        private double notesSlope;

        private double njsSlope;
    }
}
