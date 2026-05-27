package com.accsaber.backend.model.dto.request.supporter;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ClaimByRoleSignalRequest {

    @NotBlank
    private String discordId;

    @NotBlank
    private String tierName;

    private Instant assignedAt;
}
