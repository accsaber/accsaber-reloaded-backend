package com.accsaber.backend.model.dto.response.player;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.entity.user.UserRelationType;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserRelationResponse {

    UUID id;
    Long userId;
    Long targetUserId;
    String targetName;
    String targetAvatarUrl;
    String targetCountry;
    UserRelationType type;
    Instant createdAt;
}
