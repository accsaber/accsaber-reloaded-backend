package com.accsaber.backend.model.entity.campaign;

import com.accsaber.backend.model.entity.DbEnumConverter;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CampaignCollaboratorStatusConverter extends DbEnumConverter<CampaignCollaboratorStatus> {

    public CampaignCollaboratorStatusConverter() {
        super(CampaignCollaboratorStatus::getDbValue, CampaignCollaboratorStatus::fromDbValue);
    }
}
