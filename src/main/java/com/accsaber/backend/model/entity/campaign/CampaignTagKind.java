package com.accsaber.backend.model.entity.campaign;

public enum CampaignTagKind {
    CATEGORY("category"),
    DIFFICULTY("difficulty"),
    THEME("theme"),
    GENRE("genre");

    private final String dbValue;

    CampaignTagKind(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static CampaignTagKind fromDbValue(String value) {
        for (CampaignTagKind k : values()) {
            if (k.dbValue.equals(value))
                return k;
        }
        throw new IllegalArgumentException("Unknown campaign tag kind: " + value);
    }
}
