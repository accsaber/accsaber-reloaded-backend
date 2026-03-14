package com.accsaber.backend.model.entity.map;

public enum VoteType {
    UPVOTE("upvote"),
    DOWNVOTE("downvote"),
    NEUTRAL("neutral");

    private final String dbValue;

    VoteType(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static VoteType fromDbValue(String value) {
        for (VoteType v : values()) {
            if (v.dbValue.equals(value))
                return v;
        }
        throw new IllegalArgumentException("Unknown vote type: " + value);
    }
}
