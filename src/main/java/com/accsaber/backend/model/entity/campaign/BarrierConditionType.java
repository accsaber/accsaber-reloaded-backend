package com.accsaber.backend.model.entity.campaign;

public enum BarrierConditionType {
    AVERAGE_ACC,
    AVERAGE_AP,
    AP_MAX,
    ACC_MAX,
    STREAK_115_AVERAGE,
    STREAK_115_MAX,
    FC,
    AVERAGE_RANK,
    MAX_RANK;

    public boolean isLowerBetter() {
        return this == AVERAGE_RANK || this == MAX_RANK;
    }
}
