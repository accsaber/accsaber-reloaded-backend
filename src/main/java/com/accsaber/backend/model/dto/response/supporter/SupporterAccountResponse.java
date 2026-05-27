package com.accsaber.backend.model.dto.response.supporter;

import java.time.Instant;

import com.accsaber.backend.model.entity.supporter.SupporterAccount;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SupporterAccountResponse {

    private Long userId;
    private String currentTier;
    private String currentTierDisplayName;
    private Integer monthlyCostCents;
    private Instant tierStartedAt;
    private Instant lastDebitAt;
    private Integer balanceCents;
    private Long lifetimeSupportedCents;
    private boolean hasEverSupported;

    public static SupporterAccountResponse empty(Long userId) {
        return SupporterAccountResponse.builder()
                .userId(userId)
                .balanceCents(0)
                .lifetimeSupportedCents(0L)
                .hasEverSupported(false)
                .build();
    }

    public static SupporterAccountResponse from(SupporterAccount account) {
        SupporterAccountResponseBuilder b = SupporterAccountResponse.builder()
                .userId(account.getUserId())
                .balanceCents(account.getBalanceCents())
                .lifetimeSupportedCents(account.getLifetimeSupportedCents())
                .tierStartedAt(account.getTierStartedAt())
                .lastDebitAt(account.getLastDebitAt())
                .hasEverSupported(account.getLifetimeSupportedCents() > 0);
        if (account.getCurrentTier() != null) {
            b.currentTier(account.getCurrentTier().getTierKey())
                    .currentTierDisplayName(account.getCurrentTier().getDisplayName())
                    .monthlyCostCents(account.getCurrentTier().getMonthlyCostCents());
        }
        return b.build();
    }
}
