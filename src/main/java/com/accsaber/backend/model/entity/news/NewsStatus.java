package com.accsaber.backend.model.entity.news;

public enum NewsStatus {
    DRAFT("DRAFT"),
    PUBLISHED("PUBLISHED"),
    ARCHIVED("ARCHIVED");

    private final String dbValue;

    NewsStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static NewsStatus fromDbValue(String value) {
        for (NewsStatus s : values()) {
            if (s.dbValue.equals(value))
                return s;
        }
        throw new IllegalArgumentException("Unknown news status: " + value);
    }
}
