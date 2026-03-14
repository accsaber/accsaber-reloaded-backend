package com.accsaber.backend.model.entity.map;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MapDifficultyStatusConverter implements AttributeConverter<MapDifficultyStatus, String> {

    @Override
    public String convertToDatabaseColumn(MapDifficultyStatus status) {
        if (status == null) return null;
        return status.getDbValue();
    }

    @Override
    public MapDifficultyStatus convertToEntityAttribute(String value) {
        if (value == null) return null;
        return MapDifficultyStatus.fromDbValue(value);
    }
}
