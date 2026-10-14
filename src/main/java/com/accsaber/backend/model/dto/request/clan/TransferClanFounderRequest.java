package com.accsaber.backend.model.dto.request.clan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TransferClanFounderRequest(
        @NotBlank @Pattern(regexp = "^\\d+$") String userId) {
}
