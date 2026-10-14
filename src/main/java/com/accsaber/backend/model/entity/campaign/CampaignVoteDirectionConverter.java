package com.accsaber.backend.model.entity.campaign;

import com.accsaber.backend.model.entity.DbEnumConverter;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CampaignVoteDirectionConverter extends DbEnumConverter<CampaignVoteDirection> {

    public CampaignVoteDirectionConverter() {
        super(CampaignVoteDirection::getDbValue, CampaignVoteDirection::fromDbValue);
    }
}
