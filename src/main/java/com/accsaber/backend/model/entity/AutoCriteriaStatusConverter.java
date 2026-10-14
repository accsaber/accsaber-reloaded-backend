package com.accsaber.backend.model.entity;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AutoCriteriaStatusConverter extends DbEnumConverter<AutoCriteriaStatus> {

    public AutoCriteriaStatusConverter() {
        super(AutoCriteriaStatus::getDbValue, AutoCriteriaStatus::fromDbValue);
    }
}
