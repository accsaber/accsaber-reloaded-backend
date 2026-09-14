package com.accsaber.backend.model.dto.response.statistics;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserImprovementsResponse {

    private String userId;
    private String userName;
    private String avatarUrl;
    private String cdnAvatarUrl;
    private PublicClanResponse clan;
    private String country;
    private long improvementCount;
    private UUID latestScoreId;
    private Instant latestScoreTimeSet;
}
