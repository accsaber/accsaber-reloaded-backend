package com.accsaber.backend.model.entity;

public interface DbEnum {

    String getDbValue();

    static <E extends DbEnum> E fromDbValue(E[] values, String value, String label) {
        for (E constant : values) {
            if (constant.getDbValue().equals(value))
                return constant;
        }
        throw new IllegalArgumentException("Unknown " + label + ": " + value);
    }
}
