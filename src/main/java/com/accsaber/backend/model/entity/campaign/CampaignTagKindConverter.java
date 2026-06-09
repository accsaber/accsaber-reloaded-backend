package com.accsaber.backend.model.entity.campaign;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CampaignTagKindConverter implements AttributeConverter<CampaignTagKind, String> {

    @Override
    public String convertToDatabaseColumn(CampaignTagKind kind) {
        if (kind == null)
            return null;
        return kind.getDbValue();
    }

    @Override
    public CampaignTagKind convertToEntityAttribute(String value) {
        if (value == null)
            return null;
        return CampaignTagKind.fromDbValue(value);
    }
}
