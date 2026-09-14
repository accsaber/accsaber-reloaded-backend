package com.accsaber.backend.model.entity.mission;

public enum MissionType {
    PLAY_N_MAPS(MissionTrigger.SCORE, MissionProgressAxis.COUNT),
    XP_IN_WINDOW(MissionTrigger.SCORE, MissionProgressAxis.XP),
    ACC_ON_MAP(MissionTrigger.SCORE, MissionProgressAxis.BINARY),
    AP_ON_MAP(MissionTrigger.SCORE, MissionProgressAxis.BINARY),
    PB_SPECIFIC_MAP(MissionTrigger.SCORE, MissionProgressAxis.BINARY),
    PB_ABOVE_THRESHOLD(MissionTrigger.SCORE, MissionProgressAxis.COUNT),
    SNIPE_PLAYER_ON_MAP(MissionTrigger.SCORE, MissionProgressAxis.BINARY),
    STREAK_ON_MAP(MissionTrigger.SCORE, MissionProgressAxis.BINARY),
    STREAK_N_IN_CATEGORY(MissionTrigger.SCORE, MissionProgressAxis.COUNT),
    STREAK_SUM_N(MissionTrigger.SCORE, MissionProgressAxis.COUNT),
    COMEBACK_PB(MissionTrigger.SCORE, MissionProgressAxis.BINARY),
    SCORES_N(MissionTrigger.SCORE, MissionProgressAxis.COUNT),
    SNIPE_RIVAL_ANY_MAP(MissionTrigger.SCORE, MissionProgressAxis.COUNT),
    AP_GAIN_OVERALL(MissionTrigger.SCORE, MissionProgressAxis.AP),
    BATCH_PLAY_N(MissionTrigger.SCORE, MissionProgressAxis.COUNT),
    PB_RANKED_BEFORE_N(MissionTrigger.SCORE, MissionProgressAxis.COUNT),
    CAMPAIGN_COMPLETE_N(MissionTrigger.CAMPAIGN, MissionProgressAxis.COUNT);

    private final MissionTrigger trigger;
    private final MissionProgressAxis axis;

    MissionType(MissionTrigger trigger, MissionProgressAxis axis) {
        this.trigger = trigger;
        this.axis = axis;
    }

    public MissionTrigger getTrigger() {
        return trigger;
    }

    public MissionProgressAxis getAxis() {
        return axis;
    }

    public boolean hasFixedTarget() {
        return switch (this) {
            case STREAK_SUM_N, SNIPE_RIVAL_ANY_MAP, AP_GAIN_OVERALL, BATCH_PLAY_N, PB_RANKED_BEFORE_N,
                    CAMPAIGN_COMPLETE_N -> true;
            default -> false;
        };
    }
}
