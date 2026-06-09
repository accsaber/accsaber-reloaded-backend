package com.accsaber.backend.model.entity.campaign;

public enum CampaignCompletionMode {
    TERMINAL("terminal"),
    ALL("all");

    private final String dbValue;

    CampaignCompletionMode(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static CampaignCompletionMode fromDbValue(String value) {
        for (CampaignCompletionMode m : values()) {
            if (m.dbValue.equals(value))
                return m;
        }
        throw new IllegalArgumentException("Unknown campaign completion mode: " + value);
    }
}
