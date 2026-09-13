package com.accsaber.backend.model.event;

import java.util.UUID;

public record SharedMissionCompletedEvent(UUID missionId) {
}
