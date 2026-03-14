package com.accsaber.backend.model.entity.map;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DifficultyConverter implements AttributeConverter<Difficulty, String> {

    @Override
    public String convertToDatabaseColumn(Difficulty difficulty) {
        if (difficulty == null)
            return null;
        return difficulty.getDbValue();
    }

    @Override
    public Difficulty convertToEntityAttribute(String value) {
        if (value == null)
            return null;
        return Difficulty.fromDbValue(value);
    }
}
