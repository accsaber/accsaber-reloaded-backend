package com.accsaber.backend.model.dto.response.player;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserRelationCounts {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    Long followingCount;

    long followerCount;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    Long rivalCount;

    long rivaledByCount;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    Long blockedCount;
}
