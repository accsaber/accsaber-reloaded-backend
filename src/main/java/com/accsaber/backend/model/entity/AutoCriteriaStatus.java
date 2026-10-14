package com.accsaber.backend.model.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AutoCriteriaStatus implements DbEnum {
    PENDING("pending"),
    PASSED("passed"),
    FAILED("failed"),
    UNAVAILABLE("unavailable");

    private final String dbValue;

    public static AutoCriteriaStatus fromDbValue(String value) {
        return DbEnum.fromDbValue(values(), value, "auto criteria status");
    }
}
