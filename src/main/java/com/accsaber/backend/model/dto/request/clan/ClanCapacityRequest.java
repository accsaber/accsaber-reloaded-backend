package com.accsaber.backend.model.dto.request.clan;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ClanCapacityRequest(
        @NotNull @Positive Integer amount) {
}
