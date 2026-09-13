package com.accsaber.backend.model.dto.request.clan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class TransferClanFounderRequest {

    @NotBlank
    @Pattern(regexp = "^\\d+$")
    private String userId;
}
