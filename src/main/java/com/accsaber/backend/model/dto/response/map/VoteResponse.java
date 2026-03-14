package com.accsaber.backend.model.dto.response.map;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.entity.map.MapVoteAction;
import com.accsaber.backend.model.entity.map.VoteType;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class VoteResponse {

    UUID id;
    UUID mapDifficultyId;
    UUID staffId;
    VoteType vote;
    MapVoteAction type;
    BigDecimal suggestedComplexity;
    VoteType criteriaVote;
    boolean criteriaVoteOverride;
    String reason;
    boolean active;
    Instant updatedAt;
}
