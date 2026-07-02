package com.accsaber.backend.model.entity.campaign;

public enum CampaignCollaboratorStatus {
    PENDING("pending"),
    ACCEPTED("accepted"),
    DECLINED("declined");

    private final String dbValue;

    CampaignCollaboratorStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static CampaignCollaboratorStatus fromDbValue(String value) {
        for (CampaignCollaboratorStatus s : values()) {
            if (s.dbValue.equals(value))
                return s;
        }
        throw new IllegalArgumentException("Unknown campaign collaborator status: " + value);
    }
}
