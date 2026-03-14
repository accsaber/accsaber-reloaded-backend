package com.accsaber.backend.model.entity.staff;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StaffRole {

    RANKING("ranking"),
    RANKING_HEAD("ranking_head"),
    ADMIN("admin");

    private final String value;

    public static StaffRole fromValue(String value) {
        for (StaffRole role : values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown staff role: " + value);
    }
}
