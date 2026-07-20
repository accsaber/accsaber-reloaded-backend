package com.accsaber.backend.model.entity.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ScoreRowField {
    DIFFICULTY,
    ACCURACY,
    AP,
    WEIGHTED_AP,
    COMPLEXITY,
    CATEGORY,
    STREAK_115,
    PAUSES,
    PLAY_COUNT,
    DATE;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static ScoreRowField fromJson(String value) {
        if (value == null) return null;
        return ScoreRowField.valueOf(value.toUpperCase());
    }
}
