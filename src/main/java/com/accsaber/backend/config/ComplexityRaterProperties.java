package com.accsaber.backend.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.accsaber.backend.model.dto.request.map.ComplexityRaterSpec;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "accsaber.complexity.rater")
public class ComplexityRaterProperties {

    private String version = "note-acc-2026-09";
    private double worstShare = 0.05;
    private Map<String, Coefficients> categories = new LinkedHashMap<>();

    @Data
    public static class Coefficients {
        private double intercept;
        private double meanSlope;
        private double worstSlope;
    }

    public ComplexityRaterSpec toSpec() {
        ComplexityRaterSpec spec = new ComplexityRaterSpec();
        spec.setWorstShare(worstShare);
        categories.forEach((code, c) -> {
            ComplexityRaterSpec.Coefficients coefficients = new ComplexityRaterSpec.Coefficients();
            coefficients.setIntercept(c.getIntercept());
            coefficients.setMeanSlope(c.getMeanSlope());
            coefficients.setWorstSlope(c.getWorstSlope());
            spec.getCategories().put(code, coefficients);
        });
        return spec;
    }
}
