package com.accsaber.backend.model.dto.request.clan;

import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateClanWarRequest(
        @NotNull ClanWarStatus status) {
}
