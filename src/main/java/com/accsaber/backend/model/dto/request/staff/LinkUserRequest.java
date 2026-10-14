package com.accsaber.backend.model.dto.request.staff;

import jakarta.validation.constraints.NotNull;

public record LinkUserRequest(
        @NotNull Long userId) {
}
