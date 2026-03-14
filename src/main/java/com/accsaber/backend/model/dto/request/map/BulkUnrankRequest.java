package com.accsaber.backend.model.dto.request.map;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class BulkUnrankRequest {

    @NotEmpty
    @Valid
    private List<ApproveUnrankRequest> items;
}
