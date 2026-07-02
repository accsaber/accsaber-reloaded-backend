package com.accsaber.backend.model.entity.campaign;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CampaignCollaboratorStatusConverter
        implements AttributeConverter<CampaignCollaboratorStatus, String> {

    @Override
    public String convertToDatabaseColumn(CampaignCollaboratorStatus status) {
        if (status == null)
            return null;
        return status.getDbValue();
    }

    @Override
    public CampaignCollaboratorStatus convertToEntityAttribute(String value) {
        if (value == null)
            return null;
        return CampaignCollaboratorStatus.fromDbValue(value);
    }
}
