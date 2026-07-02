package com.accsaber.backend.model.entity.campaign;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CampaignLabelPositionConverter implements AttributeConverter<CampaignLabelPosition, String> {

    @Override
    public String convertToDatabaseColumn(CampaignLabelPosition position) {
        if (position == null)
            return null;
        return position.name();
    }

    @Override
    public CampaignLabelPosition convertToEntityAttribute(String value) {
        if (value == null)
            return null;
        return CampaignLabelPosition.valueOf(value);
    }
}
