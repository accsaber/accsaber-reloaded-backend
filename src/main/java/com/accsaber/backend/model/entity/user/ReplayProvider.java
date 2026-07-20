package com.accsaber.backend.model.entity.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ReplayProvider {
    BEATLEADER,
    SCORESABER,
    ARCVIEWER;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static ReplayProvider fromJson(String value) {
        if (value == null) return null;
        return ReplayProvider.valueOf(value.toUpperCase());
    }
}
