package com.accsaber.backend.model.dto.request.clan;

import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateClanWarRequest {

    @NotNull
    private ClanWarStatus status;
}
