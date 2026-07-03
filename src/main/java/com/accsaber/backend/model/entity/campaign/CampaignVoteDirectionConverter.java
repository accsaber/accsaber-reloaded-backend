package com.accsaber.backend.model.entity.campaign;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CampaignVoteDirectionConverter implements AttributeConverter<CampaignVoteDirection, String> {

    @Override
    public String convertToDatabaseColumn(CampaignVoteDirection direction) {
        if (direction == null)
            return null;
        return direction.getDbValue();
    }

    @Override
    public CampaignVoteDirection convertToEntityAttribute(String value) {
        if (value == null)
            return null;
        return CampaignVoteDirection.fromDbValue(value);
    }
}
