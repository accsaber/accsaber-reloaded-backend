package com.accsaber.backend.model.entity.campaign;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CampaignPrerequisiteModeConverter implements AttributeConverter<CampaignPrerequisiteMode, String> {

    @Override
    public String convertToDatabaseColumn(CampaignPrerequisiteMode mode) {
        if (mode == null)
            return null;
        return mode.name();
    }

    @Override
    public CampaignPrerequisiteMode convertToEntityAttribute(String value) {
        if (value == null)
            return null;
        return CampaignPrerequisiteMode.valueOf(value);
    }
}
