package com.accsaber.backend.model.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CriteriaStatus implements DbEnum {
    PENDING("pending"),
    PASSED("passed"),
    FAILED("failed");

    private final String dbValue;

    public static CriteriaStatus fromDbValue(String value) {
        return DbEnum.fromDbValue(values(), value, "criteria status");
    }
}
