package com.accsaber.backend.model.entity.news;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class NewsStatusConverter implements AttributeConverter<NewsStatus, String> {

    @Override
    public String convertToDatabaseColumn(NewsStatus status) {
        if (status == null)
            return null;
        return status.getDbValue();
    }

    @Override
    public NewsStatus convertToEntityAttribute(String value) {
        if (value == null)
            return null;
        return NewsStatus.fromDbValue(value);
    }
}
