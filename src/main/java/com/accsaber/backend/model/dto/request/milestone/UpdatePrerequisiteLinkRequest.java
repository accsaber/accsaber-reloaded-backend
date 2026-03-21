package com.accsaber.backend.model.dto.request.milestone;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdatePrerequisiteLinkRequest {

    @NotNull
    private Boolean blocker;
}
