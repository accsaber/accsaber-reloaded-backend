package com.accsaber.backend.model.dto.request.map;

import com.accsaber.backend.model.entity.map.MapDifficultyStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateMapStatusRequest {

    @NotNull
    private MapDifficultyStatus status;

    private String reason;
}
