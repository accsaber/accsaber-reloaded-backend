package com.accsaber.backend.model.dto.request.map;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record BulkUnrankRequest(
        @NotEmpty @Valid List<ApproveUnrankRequest> items) {
}
