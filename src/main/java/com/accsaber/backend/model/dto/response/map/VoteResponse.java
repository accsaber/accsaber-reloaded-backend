package com.accsaber.backend.model.dto.response.map;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.MapDifficultyRef;
import com.accsaber.backend.model.dto.response.staff.PublicStaffUserResponse;
import com.accsaber.backend.model.entity.map.MapVoteAction;
import com.accsaber.backend.model.entity.map.VoteType;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class VoteResponse {

    UUID id;
    MapDifficultyRef map;
    Double complexity;
    PublicStaffUserResponse staff;
    VoteType vote;
    MapVoteAction type;
    Double suggestedComplexity;
    VoteType criteriaVote;
    boolean criteriaVoteOverride;
    String reason;
    boolean active;
    Instant createdAt;
    Instant updatedAt;
}
