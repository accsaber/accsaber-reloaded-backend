package com.accsaber.backend.model.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CriteriaStatusConverter implements AttributeConverter<CriteriaStatus, String> {

    @Override
    public String convertToDatabaseColumn(CriteriaStatus status) {
        if (status == null)
            return null;
        return switch (status) {
            case PENDING -> "pending";
            case PASSED -> "passed";
            case FAILED -> "failed";
        };
    }

    @Override
    public CriteriaStatus convertToEntityAttribute(String dbData) {
        if (dbData == null)
            return null;
        return switch (dbData) {
            case "pending" -> CriteriaStatus.PENDING;
            case "passed" -> CriteriaStatus.PASSED;
            case "failed" -> CriteriaStatus.FAILED;
            default -> throw new IllegalArgumentException("Unknown criteria status: " + dbData);
        };
    }
}
