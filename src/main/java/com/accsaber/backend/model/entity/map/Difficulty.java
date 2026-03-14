package com.accsaber.backend.model.entity.map;

public enum Difficulty {
    EASY("Easy"),
    NORMAL("Normal"),
    HARD("Hard"),
    EXPERT("Expert"),
    EXPERT_PLUS("ExpertPlus");

    private final String dbValue;

    Difficulty(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static Difficulty fromDbValue(String value) {
        for (Difficulty d : values()) {
            if (d.dbValue.equals(value))
                return d;
        }
        throw new IllegalArgumentException("Unknown difficulty: " + value);
    }
}
