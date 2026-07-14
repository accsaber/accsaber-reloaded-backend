package com.accsaber.backend.model.dto.response.mission;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventDetailResponse {

    private EventResponse event;
    private List<MissionResponse> missions;
}
