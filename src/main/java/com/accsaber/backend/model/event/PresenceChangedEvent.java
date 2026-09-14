package com.accsaber.backend.model.event;

public record PresenceChangedEvent(Long userId, boolean online) {
}
