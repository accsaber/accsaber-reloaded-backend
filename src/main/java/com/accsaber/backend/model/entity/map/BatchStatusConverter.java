package com.accsaber.backend.model.entity.map;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class BatchStatusConverter implements AttributeConverter<BatchStatus, String> {

    @Override
    public String convertToDatabaseColumn(BatchStatus status) {
        if (status == null)
            return null;
        return status.getDbValue();
    }

    @Override
    public BatchStatus convertToEntityAttribute(String value) {
        if (value == null)
            return null;
        return BatchStatus.fromDbValue(value);
    }
}
