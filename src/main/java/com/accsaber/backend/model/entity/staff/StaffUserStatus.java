package com.accsaber.backend.model.entity.staff;

import com.accsaber.backend.model.entity.DbEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StaffUserStatus implements DbEnum {
    REQUESTED("requested"),
    ACCEPTED("accepted"),
    DENIED("denied");

    private final String dbValue;

    public static StaffUserStatus fromDbValue(String value) {
        return DbEnum.fromDbValue(values(), value, "staff user status");
    }
}
