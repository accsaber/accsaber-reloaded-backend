package com.accsaber.backend.model.dto.request.supporter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ClaimSupporterEventRequest(
        @NotBlank String kofiTransactionId,
        @NotNull Long userId) {
}
