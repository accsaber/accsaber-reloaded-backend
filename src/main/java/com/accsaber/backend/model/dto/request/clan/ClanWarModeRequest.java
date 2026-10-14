package com.accsaber.backend.model.dto.request.clan;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ClanWarModeRequest(
        @NotNull @PositiveOrZero Integer level) {
}
