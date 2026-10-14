package com.accsaber.backend.model.entity.map;

import com.accsaber.backend.model.entity.DbEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MapDifficultyStatus implements DbEnum {
    QUEUE("queue"),
    QUALIFIED("qualified"),
    RANKED("ranked"),
    CAMPAIGN("campaign");

    private final String dbValue;

    public static MapDifficultyStatus fromDbValue(String value) {
        return DbEnum.fromDbValue(values(), value, "map difficulty status");
    }
}
