package com.accsaber.backend.model.entity.campaign;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class BarrierConditionTypeConverter implements AttributeConverter<BarrierConditionType, String> {

    @Override
    public String convertToDatabaseColumn(BarrierConditionType type) {
        if (type == null)
            return null;
        return type.name();
    }

    @Override
    public BarrierConditionType convertToEntityAttribute(String value) {
        if (value == null)
            return null;
        return BarrierConditionType.valueOf(value);
    }
}
