package com.accsaber.backend.model.dto.request.clan;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record UpdateClanWarSideRequest(
        @NotNull @AssertTrue Boolean left) {
}
