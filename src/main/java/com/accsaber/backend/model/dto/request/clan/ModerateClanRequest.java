package com.accsaber.backend.model.dto.request.clan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ModerateClanRequest {

    @NotNull
    @Valid
    private UpdateClanRequest changes;

    private boolean removeIcon;

    @NotBlank
    @Size(max = 500)
    private String reason;
}
