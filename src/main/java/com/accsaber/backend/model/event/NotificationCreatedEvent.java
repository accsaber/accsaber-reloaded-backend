package com.accsaber.backend.model.event;

import com.accsaber.backend.model.dto.response.notification.NotificationResponse;

public record NotificationCreatedEvent(Long userId, NotificationResponse notification) {
}
