package com.accsaber.backend.model.dto.request.clan;

import com.accsaber.backend.model.entity.clan.ClanRole;

import jakarta.validation.constraints.NotNull;

public record UpdateClanMemberRequest(
        @NotNull ClanRole role) {
}
