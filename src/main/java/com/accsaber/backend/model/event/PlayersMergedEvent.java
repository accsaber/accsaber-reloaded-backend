package com.accsaber.backend.model.event;

public record PlayersMergedEvent(Long primaryUserId, Long secondaryUserId) {
}
