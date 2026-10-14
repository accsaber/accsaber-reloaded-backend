package com.accsaber.backend.model.dto.request.clan;

import com.accsaber.backend.model.entity.clan.ClanAllianceStatus;

import jakarta.validation.constraints.NotNull;

public record ResolveClanAllianceRequest(
        @NotNull ClanAllianceStatus status) {
}
