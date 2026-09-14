package com.accsaber.backend.model.dto.request.clan;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class ClanWarModeRequest {

    @NotNull
    @PositiveOrZero
    private Integer level;
}
