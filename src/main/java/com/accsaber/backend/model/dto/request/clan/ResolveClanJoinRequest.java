package com.accsaber.backend.model.dto.request.clan;

import com.accsaber.backend.model.entity.clan.ClanJoinStatus;

import jakarta.validation.constraints.NotNull;

public record ResolveClanJoinRequest(
        @NotNull ClanJoinStatus status) {
}
