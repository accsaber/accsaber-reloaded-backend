package com.accsaber.backend.model.dto.request.clan;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ClanCapacityRequest {

    @NotNull
    @Positive
    private Integer amount;
}
