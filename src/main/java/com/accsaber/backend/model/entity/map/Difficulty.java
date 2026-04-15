package com.accsaber.backend.model.entity.map;

public enum Difficulty {
    EASY("Easy", 1),
    NORMAL("Normal", 3),
    HARD("Hard", 5),
    EXPERT("Expert", 7),
    EXPERT_PLUS("ExpertPlus", 9);

    private final String dbValue;
    private final int numericValue;

    Difficulty(String dbValue, int numericValue) {
        this.dbValue = dbValue;
        this.numericValue = numericValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public int getNumericValue() {
        return numericValue;
    }

    public static Difficulty fromDbValue(String value) {
        for (Difficulty d : values()) {
            if (d.dbValue.equals(value))
                return d;
        }
        throw new IllegalArgumentException("Unknown difficulty: " + value);
    }
}
