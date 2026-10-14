package com.accsaber.backend.model.dto.request.map;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record BatchReweightRequest(
        @NotEmpty @Valid List<ApproveReweightRequest> items) {
}
