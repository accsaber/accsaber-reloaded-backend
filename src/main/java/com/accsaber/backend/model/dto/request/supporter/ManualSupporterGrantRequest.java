package com.accsaber.backend.model.dto.request.supporter;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ManualSupporterGrantRequest {

    @NotNull
    private Long userId;

    @NotNull
    @Min(1)
    private Integer amountCents;

    private String tierName;

    private String type;

    private String fromName;

    private String email;

    private String note;
}
