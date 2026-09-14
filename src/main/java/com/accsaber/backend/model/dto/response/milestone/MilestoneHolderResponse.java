package com.accsaber.backend.model.dto.response.milestone;

import java.time.Instant;

import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.service.clan.ClanRefCache;

import lombok.Getter;

@Getter
public class MilestoneHolderResponse {

    private final Long userId;
    private final String name;
    private final String avatarUrl;
    private final String cdnAvatarUrl;
    private final PublicClanResponse clan;
    private final String country;
    private final Instant completedAt;

    public MilestoneHolderResponse(Long userId, String name, String avatarUrl, String cdnAvatarUrl, String country,
            Instant completedAt) {
        this.userId = userId;
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.cdnAvatarUrl = cdnAvatarUrl;
        this.clan = ClanRefCache.forUser(userId);
        this.country = country;
        this.completedAt = completedAt;
    }
}
