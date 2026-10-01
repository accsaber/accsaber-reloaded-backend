package com.accsaber.backend.model.dto.request.clan;

import com.accsaber.backend.model.entity.clan.war.ClanWarLoanStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ResolveClanWarLoanRequest {

    @NotNull
    private ClanWarLoanStatus status;
}
