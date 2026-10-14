package com.accsaber.backend.model.dto.request.supporter;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;

public record ClaimByRoleSignalRequest(
        @NotBlank String discordId,
        @NotBlank String tierName,
        Instant assignedAt) {
}
