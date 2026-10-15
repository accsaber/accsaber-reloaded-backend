package com.accsaber.backend.model.dto.response.supporter;

import java.time.Instant;

import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SupporterCreditsRowResponse {

    String userId;
    String name;
    String avatarUrl;
    String cdnAvatarUrl;
    PublicClanResponse clan;
    String country;
    String currentTier;
    String currentTierDisplayName;
    Long lifetimeSupportedCents;
    Instant tierStartedAt;
    Instant firstSupportedAt;
}
