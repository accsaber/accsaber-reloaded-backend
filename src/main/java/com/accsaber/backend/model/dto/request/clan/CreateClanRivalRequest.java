package com.accsaber.backend.model.dto.request.clan;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record CreateClanRivalRequest(
        @NotNull UUID clanId) {
}
