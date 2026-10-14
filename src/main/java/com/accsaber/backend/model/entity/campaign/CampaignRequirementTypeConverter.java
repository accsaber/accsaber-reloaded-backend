package com.accsaber.backend.model.entity.campaign;

import com.accsaber.backend.model.entity.DbEnumConverter;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CampaignRequirementTypeConverter extends DbEnumConverter<CampaignRequirementType> {

    public CampaignRequirementTypeConverter() {
        super(Enum::name, CampaignRequirementType::valueOf);
    }
}
