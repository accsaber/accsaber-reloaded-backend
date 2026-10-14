package com.accsaber.backend.model.entity.campaign;

import com.accsaber.backend.model.entity.DbEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CampaignCompletionMode implements DbEnum {
    TERMINAL("terminal"),
    ALL("all");

    private final String dbValue;

    public static CampaignCompletionMode fromDbValue(String value) {
        return DbEnum.fromDbValue(values(), value, "campaign completion mode");
    }
}
