package com.accsaber.backend.model.entity.campaign;

public enum CampaignVoteDirection {
    UP("up"),
    DOWN("down");

    private final String dbValue;

    CampaignVoteDirection(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static CampaignVoteDirection fromDbValue(String value) {
        for (CampaignVoteDirection d : values()) {
            if (d.dbValue.equals(value))
                return d;
        }
        throw new IllegalArgumentException("Unknown campaign vote direction: " + value);
    }
}
