package com.accsaber.backend.model.dto.request.map;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class BulkReweightRequest {

    @NotEmpty
    @Valid
    private List<ApproveReweightRequest> items;
}
