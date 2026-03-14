package com.accsaber.backend.model.dto.request.map;

import java.util.UUID;

import com.accsaber.backend.model.entity.CriteriaStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CriteriaWebhookRequest {

    @NotNull
    private UUID mapDifficultyId;

    @NotNull
    private CriteriaStatus status;
}
