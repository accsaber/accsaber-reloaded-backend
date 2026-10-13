package com.accsaber.backend.model.dto.request.clan;

import com.accsaber.backend.model.entity.clan.war.ClanWarTeam;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OfferClanWarAidRequest {

    @NotNull
    private ClanWarTeam team;
}
