package com.accsaber.backend.model.dto.request.clan;

import com.accsaber.backend.model.entity.clan.ClanRole;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateClanMemberRequest {

    @NotNull
    private ClanRole role;
}
