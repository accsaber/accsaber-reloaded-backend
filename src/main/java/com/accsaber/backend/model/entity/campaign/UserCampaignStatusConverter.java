package com.accsaber.backend.model.entity.campaign;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UserCampaignStatusConverter implements AttributeConverter<UserCampaignStatus, String> {

    @Override
    public String convertToDatabaseColumn(UserCampaignStatus status) {
        if (status == null)
            return null;
        return status.getDbValue();
    }

    @Override
    public UserCampaignStatus convertToEntityAttribute(String value) {
        if (value == null)
            return null;
        return UserCampaignStatus.fromDbValue(value);
    }
}
