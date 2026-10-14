package com.accsaber.backend.model.entity.campaign;

import com.accsaber.backend.model.entity.DbEnumConverter;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CampaignNodeBorderLayerConverter extends DbEnumConverter<CampaignNodeBorderLayer> {

    public CampaignNodeBorderLayerConverter() {
        super(Enum::name, CampaignNodeBorderLayer::valueOf);
    }
}
