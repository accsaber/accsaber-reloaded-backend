package com.accsaber.backend.model.dto.response.milestone;

import java.time.Instant;

import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.With;

@Getter
@AllArgsConstructor
public class MilestoneHolderResponse {

    @JsonSerialize(using = ToStringSerializer.class)
    private final Long userId;
    private final String name;
    private final String avatarUrl;
    private final String cdnAvatarUrl;
    @With
    private final PublicClanResponse clan;
    private final String country;
    private final Instant completedAt;

    public MilestoneHolderResponse(Long userId, String name, String avatarUrl, String cdnAvatarUrl, String country,
            Instant completedAt) {
        this(userId, name, avatarUrl, cdnAvatarUrl, null, country, completedAt);
    }
}
