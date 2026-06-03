package com.accsaber.backend.model.event;

import com.accsaber.backend.model.dto.response.mission.MissionCompletedResponse;

public record MissionCompletedEvent(MissionCompletedResponse payload) {
}
