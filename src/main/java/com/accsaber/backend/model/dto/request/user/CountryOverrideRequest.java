package com.accsaber.backend.model.dto.request.user;

import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;

public record CountryOverrideRequest(
        @Schema(description = "Two-letter ISO 3166-1 alpha-2 code to pin the player to, or null to lift the override.", nullable = true)
        @Pattern(regexp = "^[A-Z]{2}$", message = "Country must be a 2-letter ISO 3166-1 alpha-2 code (e.g. DE, US)")
        String country) {
}
