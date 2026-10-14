package com.accsaber.backend.util;

public final class Texts {

    private Texts() {
    }

    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
