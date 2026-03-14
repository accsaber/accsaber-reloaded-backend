package com.accsaber.backend.model.dto.request.map;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApproveUnrankRequest {

    @NotNull
    private UUID mapDifficultyId;

    private String reason;
}
