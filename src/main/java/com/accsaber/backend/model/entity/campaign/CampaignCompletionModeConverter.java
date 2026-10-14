package com.accsaber.backend.model.entity.campaign;

import com.accsaber.backend.model.entity.DbEnumConverter;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CampaignCompletionModeConverter extends DbEnumConverter<CampaignCompletionMode> {

    public CampaignCompletionModeConverter() {
        super(CampaignCompletionMode::getDbValue, CampaignCompletionMode::fromDbValue);
    }
}
