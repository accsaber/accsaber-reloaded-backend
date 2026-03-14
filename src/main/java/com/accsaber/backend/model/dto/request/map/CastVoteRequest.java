package com.accsaber.backend.model.dto.request.map;

import java.math.BigDecimal;

import com.accsaber.backend.model.entity.map.MapVoteAction;
import com.accsaber.backend.model.entity.map.VoteType;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CastVoteRequest {

    @NotNull
    private VoteType vote;

    @NotNull
    private MapVoteAction type;

    private BigDecimal suggestedComplexity;

    private String reason;
}
