package com.accsaber.backend.model.dto.response.mission;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventProgressResponse {

    private EventResponse event;
    private EventProfileResponse profile;
    private boolean begun;
    private List<EventMissionProgressResponse> missions;
    private boolean bonusAwarded;

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EventMissionProgressResponse {
        private EventMissionResponse mission;
        private UserMissionResponse current;
        private long completions;
        private boolean completed;
        private boolean weekLocked;
    }
}
