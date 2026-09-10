package com.accsaber.backend.model.entity.map;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ComplexityEstimateSourceConverter implements AttributeConverter<ComplexityEstimateSource, String> {

    @Override
    public String convertToDatabaseColumn(ComplexityEstimateSource source) {
        return source == null ? null : source.getDbValue();
    }

    @Override
    public ComplexityEstimateSource convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ComplexityEstimateSource.fromDbValue(dbData);
    }
}
