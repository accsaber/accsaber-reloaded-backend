package com.accsaber.backend.model.entity.campaign;

import com.accsaber.backend.model.entity.DbEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CampaignTagKind implements DbEnum {
    CATEGORY("category"),
    DIFFICULTY("difficulty"),
    THEME("theme"),
    GENRE("genre");

    private final String dbValue;

    public static CampaignTagKind fromDbValue(String value) {
        return DbEnum.fromDbValue(values(), value, "campaign tag kind");
    }
}
