package com.accsaber.backend.model.dto.response.notification;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.entity.notification.NotificationType;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationResponse {

    private UUID id;
    private NotificationType type;
    private String title;
    private String linkTo;
    private boolean read;
    private Instant createdAt;
}
