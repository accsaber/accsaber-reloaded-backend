package com.accsaber.backend.model.entity.campaign;

public enum CampaignStatus {
    DRAFT("draft"),
    PUBLISHED("published"),
    EDITING("editing"),
    CURATED("curated");

    private final String dbValue;

    CampaignStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static CampaignStatus fromDbValue(String value) {
        for (CampaignStatus s : values()) {
            if (s.dbValue.equals(value))
                return s;
        }
        throw new IllegalArgumentException("Unknown campaign status: " + value);
    }
}
