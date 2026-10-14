package com.accsaber.backend.model.entity;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CriteriaStatusConverter extends DbEnumConverter<CriteriaStatus> {

    public CriteriaStatusConverter() {
        super(CriteriaStatus::getDbValue, CriteriaStatus::fromDbValue);
    }
}
