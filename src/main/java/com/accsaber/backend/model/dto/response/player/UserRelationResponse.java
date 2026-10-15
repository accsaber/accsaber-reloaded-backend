package com.accsaber.backend.model.dto.response.player;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.entity.user.UserRelationType;

import lombok.Builder;
import lombok.Value;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Value
@Builder
public class UserRelationResponse {

    UUID id;
    @JsonSerialize(using = ToStringSerializer.class)
    Long userId;
    @JsonSerialize(using = ToStringSerializer.class)
    Long targetUserId;
    String targetName;
    String targetAvatarUrl;
    String targetCdnAvatarUrl;
    PublicClanResponse targetClan;
    String targetCountry;
    UserRelationType type;
    Instant createdAt;
    boolean hidden;
}
