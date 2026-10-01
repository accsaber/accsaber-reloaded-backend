package com.accsaber.backend.model.dto.request.clan;

import com.accsaber.backend.model.entity.clan.ClanAllianceStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ResolveClanAllianceRequest {

    @NotNull
    private ClanAllianceStatus status;
}
