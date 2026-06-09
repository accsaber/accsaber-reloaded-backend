package com.accsaber.backend.model.entity.campaign;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CampaignCompletionModeConverter implements AttributeConverter<CampaignCompletionMode, String> {

    @Override
    public String convertToDatabaseColumn(CampaignCompletionMode mode) {
        if (mode == null)
            return null;
        return mode.getDbValue();
    }

    @Override
    public CampaignCompletionMode convertToEntityAttribute(String value) {
        if (value == null)
            return null;
        return CampaignCompletionMode.fromDbValue(value);
    }
}
