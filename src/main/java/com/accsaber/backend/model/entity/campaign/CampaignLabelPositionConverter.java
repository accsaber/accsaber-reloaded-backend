package com.accsaber.backend.model.entity.campaign;

import com.accsaber.backend.model.entity.DbEnumConverter;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CampaignLabelPositionConverter extends DbEnumConverter<CampaignLabelPosition> {

    public CampaignLabelPositionConverter() {
        super(Enum::name, CampaignLabelPosition::valueOf);
    }
}
