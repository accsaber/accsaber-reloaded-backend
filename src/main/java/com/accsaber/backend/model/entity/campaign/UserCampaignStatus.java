package com.accsaber.backend.model.entity.campaign;

public enum UserCampaignStatus {
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    ABANDONED("abandoned");

    private final String dbValue;

    UserCampaignStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static UserCampaignStatus fromDbValue(String value) {
        for (UserCampaignStatus s : values()) {
            if (s.dbValue.equals(value))
                return s;
        }
        throw new IllegalArgumentException("Unknown user campaign status: " + value);
    }
}
