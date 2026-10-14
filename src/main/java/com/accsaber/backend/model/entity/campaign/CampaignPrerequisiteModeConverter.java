package com.accsaber.backend.model.entity.campaign;

import com.accsaber.backend.model.entity.DbEnumConverter;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CampaignPrerequisiteModeConverter extends DbEnumConverter<CampaignPrerequisiteMode> {

    public CampaignPrerequisiteModeConverter() {
        super(Enum::name, CampaignPrerequisiteMode::valueOf);
    }
}
