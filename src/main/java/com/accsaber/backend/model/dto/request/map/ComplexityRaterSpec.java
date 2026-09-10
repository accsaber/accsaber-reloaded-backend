package com.accsaber.backend.model.dto.request.map;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ComplexityRaterSpec {

    @NotNull
    @DecimalMin("0.005")
    @DecimalMax("0.5")
    private Double worstShare;

    @NotEmpty
    @Valid
    private Map<String, Coefficients> categories = new LinkedHashMap<>();

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
    }
}
