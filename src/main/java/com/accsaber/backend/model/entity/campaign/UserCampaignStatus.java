package com.accsaber.backend.model.entity.campaign;

import java.util.List;

import com.accsaber.backend.model.entity.DbEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserCampaignStatus implements DbEnum {
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    ABANDONED("abandoned");

    public static final List<UserCampaignStatus> PARTICIPATING = List.of(IN_PROGRESS, COMPLETED);

    private final String dbValue;

    public static UserCampaignStatus fromDbValue(String value) {
        return DbEnum.fromDbValue(values(), value, "user campaign status");
    }
}
