package com.accsaber.backend.model.dto.request.clan;

import com.accsaber.backend.model.entity.clan.ClanJoinStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ResolveClanJoinRequest {

    @NotNull
    private ClanJoinStatus status;
}
