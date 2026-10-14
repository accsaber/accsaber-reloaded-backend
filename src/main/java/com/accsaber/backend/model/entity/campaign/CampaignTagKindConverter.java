package com.accsaber.backend.model.entity.campaign;

import com.accsaber.backend.model.entity.DbEnumConverter;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CampaignTagKindConverter extends DbEnumConverter<CampaignTagKind> {

    public CampaignTagKindConverter() {
        super(CampaignTagKind::getDbValue, CampaignTagKind::fromDbValue);
    }
}
