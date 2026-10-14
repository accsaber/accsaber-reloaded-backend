package com.accsaber.backend.model.entity.campaign;

import com.accsaber.backend.model.entity.DbEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CampaignCollaboratorStatus implements DbEnum {
    PENDING("pending"),
    ACCEPTED("accepted"),
    DECLINED("declined");

    private final String dbValue;

    public static CampaignCollaboratorStatus fromDbValue(String value) {
        return DbEnum.fromDbValue(values(), value, "campaign collaborator status");
    }
}
