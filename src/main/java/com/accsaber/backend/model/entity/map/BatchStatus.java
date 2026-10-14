package com.accsaber.backend.model.entity.map;

import com.accsaber.backend.model.entity.DbEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BatchStatus implements DbEnum {
    DRAFT("draft"),
    RELEASE_READY("release_ready"),
    RELEASED("released");

    private final String dbValue;

    public static BatchStatus fromDbValue(String value) {
        return DbEnum.fromDbValue(values(), value, "batch status");
    }
}
