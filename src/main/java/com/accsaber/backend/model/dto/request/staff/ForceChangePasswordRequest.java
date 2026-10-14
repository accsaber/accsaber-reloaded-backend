package com.accsaber.backend.model.dto.request.staff;

import jakarta.validation.constraints.NotBlank;

public record ForceChangePasswordRequest(
        @NotBlank String newPassword) {
}
