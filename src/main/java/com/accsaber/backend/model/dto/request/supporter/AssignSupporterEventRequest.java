package com.accsaber.backend.model.dto.request.supporter;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AssignSupporterEventRequest {

    @NotBlank
    private String kofiTransactionId;

    @NotBlank
    private String discordId;
}
