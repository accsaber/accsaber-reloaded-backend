package com.accsaber.backend.model.entity;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CurveTypeConverter extends DbEnumConverter<CurveType> {

    public CurveTypeConverter() {
        super(Enum::name, CurveType::valueOf);
    }
}
