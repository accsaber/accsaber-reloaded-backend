package com.accsaber.backend.model.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AutoCriteriaStatusConverter implements AttributeConverter<AutoCriteriaStatus, String> {

    @Override
    public String convertToDatabaseColumn(AutoCriteriaStatus status) {
        if (status == null)
            return null;
        return switch (status) {
            case PENDING -> "pending";
            case PASSED -> "passed";
            case FAILED -> "failed";
            case UNAVAILABLE -> "unavailable";
        };
    }

    @Override
    public AutoCriteriaStatus convertToEntityAttribute(String dbData) {
        if (dbData == null)
            return null;
        return switch (dbData) {
            case "pending" -> AutoCriteriaStatus.PENDING;
            case "passed" -> AutoCriteriaStatus.PASSED;
            case "failed" -> AutoCriteriaStatus.FAILED;
            case "unavailable" -> AutoCriteriaStatus.UNAVAILABLE;
            default -> throw new IllegalArgumentException("Unknown auto criteria status: " + dbData);
        };
    }
}
