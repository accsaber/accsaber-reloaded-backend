package com.accsaber.backend.model.entity.campaign;

import com.accsaber.backend.model.entity.DbEnumConverter;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CampaignStatusConverter extends DbEnumConverter<CampaignStatus> {

    public CampaignStatusConverter() {
        super(CampaignStatus::getDbValue, CampaignStatus::fromDbValue);
    }
}
