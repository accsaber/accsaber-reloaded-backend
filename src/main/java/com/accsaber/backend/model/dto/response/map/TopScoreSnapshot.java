package com.accsaber.backend.model.dto.response.map;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TopScoreSnapshot {

    UUID scoreId;
    String userId;
    String userName;
    String avatarUrl;
    String cdnAvatarUrl;
    PublicClanResponse clan;
    Integer score;
    Double accuracy;
    Double ap;
    Instant timeSet;
}
