package com.accsaber.backend.model.dto.request.supporter;

import jakarta.validation.constraints.NotBlank;

public record AssignSupporterEventRequest(
        @NotBlank String kofiTransactionId,
        @NotBlank String discordId) {
}
