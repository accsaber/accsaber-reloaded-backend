package com.accsaber.backend.model.dto.request.notification;

import com.accsaber.backend.validation.CleanText;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BroadcastRequest(
        @NotBlank @Size(max = 200) @CleanText String title,
        @Size(max = 500) String linkTo) {
}
