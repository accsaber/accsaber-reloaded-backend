package com.accsaber.backend.model.entity.campaign;

import com.accsaber.backend.model.entity.DbEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CampaignVoteDirection implements DbEnum {
    UP("up"),
    DOWN("down");

    private final String dbValue;

    public static CampaignVoteDirection fromDbValue(String value) {
        return DbEnum.fromDbValue(values(), value, "campaign vote direction");
    }
}
