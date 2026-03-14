package com.accsaber.backend.model.entity.map;

public enum MapDifficultyStatus {
    QUEUE("queue"),
    QUALIFIED("qualified"),
    RANKED("ranked");

    private final String dbValue;

    MapDifficultyStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static MapDifficultyStatus fromDbValue(String value) {
        for (MapDifficultyStatus s : values()) {
            if (s.dbValue.equals(value))
                return s;
        }
        throw new IllegalArgumentException("Unknown map difficulty status: " + value);
    }
}
