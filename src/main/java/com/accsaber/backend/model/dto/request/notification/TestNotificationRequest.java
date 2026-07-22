package com.accsaber.backend.model.dto.request.notification;

import com.accsaber.backend.model.entity.notification.NotificationType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TestNotificationRequest {

    @NotNull
    private Long userId;

    @NotNull
    private NotificationType type;

    @Size(max = 200)
    private String title;

    @Size(max = 500)
    private String linkTo;
}
