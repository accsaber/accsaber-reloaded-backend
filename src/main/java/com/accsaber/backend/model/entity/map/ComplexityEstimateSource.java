package com.accsaber.backend.model.entity.map;

public enum ComplexityEstimateSource {
    OLD_SCRIPT("old_script"),
    NEW_SCRIPT("new_script");

    private final String dbValue;

    ComplexityEstimateSource(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static ComplexityEstimateSource fromDbValue(String value) {
        for (ComplexityEstimateSource source : values()) {
            if (source.dbValue.equals(value)) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unknown complexity estimate source: " + value);
    }
}
