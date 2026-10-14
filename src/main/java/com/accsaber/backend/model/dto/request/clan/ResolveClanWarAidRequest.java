package com.accsaber.backend.model.dto.request.clan;

import com.accsaber.backend.model.entity.clan.war.ClanWarAidStatus;

import jakarta.validation.constraints.NotNull;

public record ResolveClanWarAidRequest(
        @NotNull ClanWarAidStatus status) {
}
