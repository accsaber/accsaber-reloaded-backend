package com.accsaber.backend.model.dto.request.milestone;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateMilestoneSetRequest {

    @NotBlank
    private String title;

    private String description;

    private BigDecimal setBonusXp;
}
