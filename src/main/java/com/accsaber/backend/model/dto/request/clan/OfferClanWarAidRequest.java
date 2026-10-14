package com.accsaber.backend.model.dto.request.clan;

import com.accsaber.backend.model.entity.clan.war.ClanWarTeam;

import jakarta.validation.constraints.NotNull;

public record OfferClanWarAidRequest(
        @NotNull ClanWarTeam team) {
}
