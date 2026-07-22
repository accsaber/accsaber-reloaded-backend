package com.accsaber.backend.service.notification;

import com.accsaber.backend.model.dto.response.notification.NotificationResponse;
import com.accsaber.backend.model.entity.notification.Notification;

public final class NotificationMapper {

    private NotificationMapper() {
    }

    public static NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .linkTo(notification.getLinkTo())
                .read(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
