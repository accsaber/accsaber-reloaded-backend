package com.accsaber.backend.service.map;

import java.util.List;

import com.accsaber.backend.model.entity.map.ComplexityEstimateSource;

public enum ComplexityScenario {
    CURRENT(null),
    OLD_SCRIPT(ComplexityEstimateSource.OLD_SCRIPT),
    NEW_SCRIPT(ComplexityEstimateSource.NEW_SCRIPT),
    PREVIEW(null);

    public static final List<ComplexityScenario> STORED = List.of(CURRENT, OLD_SCRIPT, NEW_SCRIPT);

    private final ComplexityEstimateSource source;

    ComplexityScenario(ComplexityEstimateSource source) {
        this.source = source;
    }

    public ComplexityEstimateSource source() {
        return source;
    }
}
