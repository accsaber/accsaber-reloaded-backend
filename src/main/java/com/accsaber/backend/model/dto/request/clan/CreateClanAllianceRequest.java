package com.accsaber.backend.model.dto.request.clan;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateClanAllianceRequest {

    @NotNull
    private UUID clanId;
}
