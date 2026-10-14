package com.accsaber.backend.model.entity.news;

import com.accsaber.backend.model.entity.DbEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NewsStatus implements DbEnum {
    DRAFT("DRAFT"),
    PUBLISHED("PUBLISHED"),
    ARCHIVED("ARCHIVED");

    private final String dbValue;

    public static NewsStatus fromDbValue(String value) {
        return DbEnum.fromDbValue(values(), value, "news status");
    }
}
