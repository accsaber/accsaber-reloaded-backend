package com.accsaber.backend.model.entity.map;

import com.accsaber.backend.model.entity.DbEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VoteType implements DbEnum {
    UPVOTE("upvote"),
    DOWNVOTE("downvote"),
    NEUTRAL("neutral");

    private final String dbValue;

    public static VoteType fromDbValue(String value) {
        return DbEnum.fromDbValue(values(), value, "vote type");
    }
}
