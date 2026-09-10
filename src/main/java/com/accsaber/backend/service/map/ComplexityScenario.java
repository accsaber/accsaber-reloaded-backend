package com.accsaber.backend.service.map;

import java.util.List;

public enum ComplexityScenario {
    CURRENT,
    NEW_SCRIPT,
    PREVIEW;

    public static final List<ComplexityScenario> STORED = List.of(CURRENT, NEW_SCRIPT);

    public boolean stored() {
        return this != PREVIEW;
    }
}
