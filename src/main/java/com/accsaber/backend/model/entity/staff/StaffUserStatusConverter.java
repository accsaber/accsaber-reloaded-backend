package com.accsaber.backend.model.entity.staff;

import com.accsaber.backend.model.entity.DbEnumConverter;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class StaffUserStatusConverter extends DbEnumConverter<StaffUserStatus> {

    public StaffUserStatusConverter() {
        super(StaffUserStatus::getDbValue, StaffUserStatus::fromDbValue);
    }
}
