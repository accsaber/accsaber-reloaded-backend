package com.accsaber.backend.model.entity.staff;

import com.accsaber.backend.model.entity.DbEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StaffRole implements DbEnum {

    MODERATOR("moderator"),
    RANKING("ranking"),
    RANKING_HEAD("ranking_head"),
    DEVELOPER("developer"),
    CAMPAIGN_CURATOR("campaign_curator"),
    CREATIVE("creative"),
    ADMIN("admin");

    private final String dbValue;

    public static StaffRole fromDbValue(String value) {
        return DbEnum.fromDbValue(values(), value, "staff role");
    }
}
