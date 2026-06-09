package com.accsaber.backend.model.entity.campaign;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CampaignRequirementTypeConverter implements AttributeConverter<CampaignRequirementType, String> {

    @Override
    public String convertToDatabaseColumn(CampaignRequirementType type) {
        if (type == null)
            return null;
        return type.name();
    }

    @Override
    public CampaignRequirementType convertToEntityAttribute(String value) {
        if (value == null)
            return null;
        return CampaignRequirementType.valueOf(value);
    }
}
