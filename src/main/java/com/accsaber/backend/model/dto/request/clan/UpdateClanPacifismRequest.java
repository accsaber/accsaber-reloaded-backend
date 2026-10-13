package com.accsaber.backend.model.dto.request.clan;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateClanPacifismRequest {

    @NotNull
    private Boolean enabled;
}
