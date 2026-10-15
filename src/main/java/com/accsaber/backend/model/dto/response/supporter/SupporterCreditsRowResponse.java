package com.accsaber.backend.model.dto.response.supporter;

import java.time.Instant;

import com.accsaber.backend.model.dto.response.common.PlayerRef;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SupporterCreditsRowResponse {

    PlayerRef player;
    String currentTier;
    String currentTierDisplayName;
    Long lifetimeSupportedCents;
    Instant tierStartedAt;
    Instant firstSupportedAt;
}
