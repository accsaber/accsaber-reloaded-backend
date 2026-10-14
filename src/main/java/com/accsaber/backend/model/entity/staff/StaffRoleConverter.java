package com.accsaber.backend.model.entity.staff;

import com.accsaber.backend.model.entity.DbEnumConverter;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class StaffRoleConverter extends DbEnumConverter<StaffRole> {

    public StaffRoleConverter() {
        super(StaffRole::getDbValue, StaffRole::fromDbValue);
    }
}
