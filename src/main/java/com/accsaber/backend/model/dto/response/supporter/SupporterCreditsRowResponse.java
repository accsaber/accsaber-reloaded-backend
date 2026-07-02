package com.accsaber.backend.model.dto.response.supporter;

import java.time.Instant;

import com.accsaber.backend.model.entity.supporter.SupporterAccount;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SupporterCreditsRowResponse {

    Long userId;
    String name;
    String avatarUrl;
    String cdnAvatarUrl;
    String country;
    String currentTier;
    String currentTierDisplayName;
    Long lifetimeSupportedCents;
    Instant tierStartedAt;
    Instant firstSupportedAt;

    public static SupporterCreditsRowResponse from(SupporterAccount account) {
        SupporterCreditsRowResponseBuilder b = SupporterCreditsRowResponse.builder()
                .userId(account.getUserId())
                .name(account.getUser().getName())
                .avatarUrl(account.getUser().getAvatarUrl())
                .cdnAvatarUrl(account.getUser().getCdnAvatarUrl())
                .country(account.getUser().getCountry())
                .lifetimeSupportedCents(account.getLifetimeSupportedCents())
                .tierStartedAt(account.getTierStartedAt())
                .firstSupportedAt(account.getCreatedAt());
        if (account.getCurrentTier() != null) {
            b.currentTier(account.getCurrentTier().getTierKey())
                    .currentTierDisplayName(account.getCurrentTier().getDisplayName());
        }
        return b.build();
    }
}
