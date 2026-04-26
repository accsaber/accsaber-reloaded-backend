package com.accsaber.backend.model.entity.news;

public enum NewsType {
    BATCH,
    CAMPAIGN,
    MILESTONE_SET,
    CURVE,
    GENERAL;

    public static NewsType of(News news) {
        if (news.getBatch() != null)
            return BATCH;
        if (news.getCampaign() != null)
            return CAMPAIGN;
        if (news.getMilestoneSet() != null)
            return MILESTONE_SET;
        if (news.getCurve() != null)
            return CURVE;
        return GENERAL;
    }

    public int filterCode() {
        return switch (this) {
            case BATCH -> 1;
            case CAMPAIGN -> 2;
            case MILESTONE_SET -> 3;
            case CURVE -> 4;
            case GENERAL -> 5;
        };
    }
}
