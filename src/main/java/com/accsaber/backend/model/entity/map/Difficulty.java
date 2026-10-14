package com.accsaber.backend.model.entity.map;

import com.accsaber.backend.model.entity.DbEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Difficulty implements DbEnum {
    EASY("Easy", 1),
    NORMAL("Normal", 3),
    HARD("Hard", 5),
    EXPERT("Expert", 7),
    EXPERT_PLUS("ExpertPlus", 9);

    private final String dbValue;
    private final int numericValue;

    public static Difficulty fromDbValue(String value) {
        return DbEnum.fromDbValue(values(), value, "difficulty");
    }
}
