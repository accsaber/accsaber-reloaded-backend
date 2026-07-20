package com.accsaber.backend.model.entity.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ComplexityNumberStyle {
    COLORED,
    PLAIN;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static ComplexityNumberStyle fromJson(String value) {
        if (value == null) return null;
        return ComplexityNumberStyle.valueOf(value.toUpperCase());
    }
}
