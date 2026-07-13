package com.accsaber.backend.model.dto.response.mission;

import java.time.Instant;

import com.accsaber.backend.model.entity.mission.UserEventProfile;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventProfileResponse {

    private int unlockedWeek;
    private int missionsCompleted;
    private Instant startedAt;
    private Instant completedAt;
    private boolean bonusAwarded;
    private Integer bonusXp;

    public static EventProfileResponse from(UserEventProfile profile) {
        return EventProfileResponse.builder()
                .unlockedWeek(profile.getUnlockedWeek())
                .missionsCompleted(profile.getMissionsCompleted())
                .startedAt(profile.getStartedAt())
                .completedAt(profile.getCompletedAt())
                .bonusAwarded(profile.getBonusAwardedAt() != null)
                .bonusXp(profile.getBonusXp())
                .build();
    }
}
