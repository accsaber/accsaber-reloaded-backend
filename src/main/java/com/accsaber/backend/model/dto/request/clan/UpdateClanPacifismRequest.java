package com.accsaber.backend.model.dto.request.clan;

import jakarta.validation.constraints.NotNull;

public record UpdateClanPacifismRequest(
        @NotNull Boolean enabled) {
}
