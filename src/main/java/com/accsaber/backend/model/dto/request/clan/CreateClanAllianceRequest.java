package com.accsaber.backend.model.dto.request.clan;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record CreateClanAllianceRequest(
        @NotNull UUID clanId) {
}
