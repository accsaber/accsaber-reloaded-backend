package com.accsaber.backend.model.entity.mission;

public enum MissionType {
    PLAY_N_MAPS(MissionTrigger.SCORE),
    XP_IN_WINDOW(MissionTrigger.SCORE),
    ACC_ON_MAP(MissionTrigger.SCORE),
    AP_ON_MAP(MissionTrigger.SCORE),
    PB_SPECIFIC_MAP(MissionTrigger.SCORE),
    PB_ABOVE_THRESHOLD(MissionTrigger.SCORE),
    SNIPE_PLAYER_ON_MAP(MissionTrigger.SCORE),
    STREAK_ON_MAP(MissionTrigger.SCORE),
    STREAK_N_IN_CATEGORY(MissionTrigger.SCORE),
    STREAK_SUM_N(MissionTrigger.SCORE),
    COMEBACK_PB(MissionTrigger.SCORE),
    SCORES_N(MissionTrigger.SCORE),
    SNIPE_RIVAL_ANY_MAP(MissionTrigger.SCORE),
    AP_GAIN_OVERALL(MissionTrigger.SCORE),
    BATCH_PLAY_N(MissionTrigger.SCORE),
    PB_RANKED_BEFORE_N(MissionTrigger.SCORE),
    CAMPAIGN_COMPLETE_N(MissionTrigger.CAMPAIGN);

    private final MissionTrigger trigger;

    MissionType(MissionTrigger trigger) {
        this.trigger = trigger;
    }

    public MissionTrigger getTrigger() {
        return trigger;
    }
}
