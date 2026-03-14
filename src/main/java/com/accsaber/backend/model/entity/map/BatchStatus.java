package com.accsaber.backend.model.entity.map;

public enum BatchStatus {
    DRAFT("draft"),
    RELEASE_READY("release_ready"),
    RELEASED("released");

    private final String dbValue;

    BatchStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static BatchStatus fromDbValue(String value) {
        for (BatchStatus s : values()) {
            if (s.dbValue.equals(value))
                return s;
        }
        throw new IllegalArgumentException("Unknown batch status: " + value);
    }
}
