package com.accsaber.backend.model.dto.request.milestone;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreatePrerequisiteLinkRequest {

    @NotNull
    private UUID milestoneId;

    @NotNull
    private UUID prerequisiteMilestoneId;

    private boolean blocker = false;
}
