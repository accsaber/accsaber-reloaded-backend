package com.accsaber.backend.model.dto.response.player;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserRelationCounts {

    long followingCount;
    long followerCount;
    long rivalCount;
    long rivaledByCount;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    Long blockedCount;
}
