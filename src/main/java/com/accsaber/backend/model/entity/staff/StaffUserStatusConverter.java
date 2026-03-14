package com.accsaber.backend.model.entity.staff;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class StaffUserStatusConverter implements AttributeConverter<StaffUserStatus, String> {

    @Override
    public String convertToDatabaseColumn(StaffUserStatus status) {
        if (status == null)
            return null;
        return switch (status) {
            case REQUESTED -> "requested";
            case ACCEPTED -> "accepted";
            case DENIED -> "denied";
        };
    }

    @Override
    public StaffUserStatus convertToEntityAttribute(String dbData) {
        if (dbData == null)
            return null;
        return switch (dbData) {
            case "requested" -> StaffUserStatus.REQUESTED;
            case "accepted" -> StaffUserStatus.ACCEPTED;
            case "denied" -> StaffUserStatus.DENIED;
            default -> throw new IllegalArgumentException("Unknown staff user status: " + dbData);
        };
    }
}
