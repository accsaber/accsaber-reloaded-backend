package com.accsaber.backend.model.dto.response.map;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapVoteAction;
import com.accsaber.backend.model.entity.map.VoteType;
import com.accsaber.backend.model.entity.staff.StaffRole;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class VoteResponse {

    UUID id;
    UUID mapDifficultyId;
    Difficulty difficulty;
    Double complexity;
    String songName;
    String songSubName;
    String songAuthor;
    String mapAuthor;
    String coverUrl;
    String cdnCoverUrl;
    UUID staffId;
    String staffUsername;
    String staffAvatarUrl;
    StaffRole staffRole;
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
