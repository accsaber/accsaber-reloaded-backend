package com.accsaber.backend.model.entity.campaign;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CampaignStatusConverter implements AttributeConverter<CampaignStatus, String> {

    @Override
    public String convertToDatabaseColumn(CampaignStatus status) {
        if (status == null)
            return null;
        return status.getDbValue();
    }

    @Override
    public CampaignStatus convertToEntityAttribute(String value) {
        if (value == null)
            return null;
        return CampaignStatus.fromDbValue(value);
    }
}
