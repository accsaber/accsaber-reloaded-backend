package com.accsaber.backend.model.entity.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Visibility {
    PUBLIC,
    FOLLOWERS_ONLY,
    PRIVATE;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static Visibility fromJson(String value) {
        if (value == null) return null;
        return Visibility.valueOf(value.toUpperCase());
    }
}
