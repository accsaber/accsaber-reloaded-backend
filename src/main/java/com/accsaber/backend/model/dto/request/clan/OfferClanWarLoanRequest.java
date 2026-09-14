package com.accsaber.backend.model.dto.request.clan;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class OfferClanWarLoanRequest {

    @NotBlank
    @Pattern(regexp = "^\\d+$")
    private String userId;

    @NotNull
    private UUID clanId;
}
