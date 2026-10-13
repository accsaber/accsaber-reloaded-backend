package com.accsaber.backend.model.dto.request.clan;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateClanWarSideRequest {

    @NotNull
    @AssertTrue
    private Boolean left;
}
