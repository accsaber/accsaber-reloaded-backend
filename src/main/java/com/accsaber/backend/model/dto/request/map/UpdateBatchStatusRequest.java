package com.accsaber.backend.model.dto.request.map;

import com.accsaber.backend.model.entity.map.BatchStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateBatchStatusRequest {

    @NotNull
    private BatchStatus status;
}
