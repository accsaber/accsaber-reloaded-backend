package com.accsaber.backend.model.dto.request.supporter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ClaimSupporterEventRequest {

    @NotBlank
    private String kofiTransactionId;

    @NotNull
    private Long userId;
}
