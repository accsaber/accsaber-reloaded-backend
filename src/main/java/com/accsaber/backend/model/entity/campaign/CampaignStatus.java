package com.accsaber.backend.model.entity.campaign;

import com.accsaber.backend.model.entity.DbEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CampaignStatus implements DbEnum {
    DRAFT("draft"),
    PUBLISHED("published"),
    EDITING("editing"),
    CURATED("curated");

    private final String dbValue;

    public static CampaignStatus fromDbValue(String value) {
        return DbEnum.fromDbValue(values(), value, "campaign status");
    }
}
