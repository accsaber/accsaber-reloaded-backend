package com.accsaber.backend.model.entity.campaign;

import com.accsaber.backend.model.entity.DbEnumConverter;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UserCampaignStatusConverter extends DbEnumConverter<UserCampaignStatus> {

    public UserCampaignStatusConverter() {
        super(UserCampaignStatus::getDbValue, UserCampaignStatus::fromDbValue);
    }
}
