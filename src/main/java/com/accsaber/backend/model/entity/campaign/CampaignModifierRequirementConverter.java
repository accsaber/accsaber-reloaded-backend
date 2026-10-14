package com.accsaber.backend.model.entity.campaign;

import com.accsaber.backend.model.entity.DbEnumConverter;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CampaignModifierRequirementConverter extends DbEnumConverter<CampaignModifierRequirement> {

    public CampaignModifierRequirementConverter() {
        super(Enum::name, CampaignModifierRequirement::valueOf);
    }
}
