package com.accsaber.backend.model.dto.request.clan;

import com.accsaber.backend.model.entity.clan.war.ClanWarAidStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ResolveClanWarAidRequest {

    @NotNull
    private ClanWarAidStatus status;
}
