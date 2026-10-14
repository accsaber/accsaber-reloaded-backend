package com.accsaber.backend.model.entity.map;

import com.accsaber.backend.model.entity.DbEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MapVoteAction implements DbEnum {
    RANK("rank"),
    REWEIGHT("reweight"),
    UNRANK("unrank");

    private final String dbValue;

    public static MapVoteAction fromDbValue(String value) {
        return DbEnum.fromDbValue(values(), value, "map vote action");
    }
}
