package com.accsaber.backend.model.dto.response.statistics;

import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;

public record UserIdentity(String userId, String userName, String avatarUrl, String cdnAvatarUrl,
        PublicClanResponse clan, String country) {
}
