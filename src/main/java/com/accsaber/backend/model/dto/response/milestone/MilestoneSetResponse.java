package com.accsaber.backend.model.dto.response.milestone;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MilestoneSetResponse {

    private UUID id;
    private String title;
    private String description;
    private BigDecimal setBonusXp;
    private Instant createdAt;
}
