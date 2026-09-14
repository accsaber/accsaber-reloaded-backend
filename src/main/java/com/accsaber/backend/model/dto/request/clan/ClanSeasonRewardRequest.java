package com.accsaber.backend.model.dto.request.clan;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ClanSeasonRewardRequest {

    @NotNull
    @Positive
    private Integer rankFrom;

    @NotNull
    @Positive
    private Integer rankTo;

    @NotNull
    private UUID itemId;

    @Positive
    private Integer quantity;
}
