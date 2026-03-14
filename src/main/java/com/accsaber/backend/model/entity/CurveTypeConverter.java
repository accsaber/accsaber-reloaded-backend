package com.accsaber.backend.model.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CurveTypeConverter implements AttributeConverter<CurveType, String> {

    @Override
    public String convertToDatabaseColumn(CurveType curveType) {
        if (curveType == null) {
            return null;
        }
        return curveType.name();
    }

    @Override
    public CurveType convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        return CurveType.valueOf(value);
    }
}
