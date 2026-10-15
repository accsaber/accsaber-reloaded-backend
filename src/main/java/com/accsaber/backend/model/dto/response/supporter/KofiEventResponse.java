package com.accsaber.backend.model.dto.response.supporter;

import java.time.Instant;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.supporter.KofiEvent;
import com.accsaber.backend.service.clan.ClanRefCache;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class KofiEventResponse {

    String kofiTransactionId;
    String type;
    String email;
    String fromName;
    Integer amountCents;
    String currency;
    String tierName;
    boolean subscription;
    boolean firstSubscription;
    Instant receivedAt;
    PlayerRef claimedUser;
    Instant claimedAt;
    String claimSource;

    public static KofiEventResponse from(KofiEvent event) {
        KofiEventResponseBuilder b = KofiEventResponse.builder()
                .kofiTransactionId(event.getKofiTransactionId())
                .type(event.getType().name())
                .email(event.getEmail())
                .fromName(event.getFromName())
                .amountCents(event.getAmountCents())
                .currency(event.getCurrency())
                .tierName(event.getTierName())
                .subscription(event.isSubscription())
                .firstSubscription(event.isFirstSubscription())
                .receivedAt(event.getReceivedAt())
                .claimedUser(ClanRefCache.playerRef(event.getClaimedUser()))
                .claimedAt(event.getClaimedAt());
        if (event.getClaimSource() != null) {
            b.claimSource(event.getClaimSource().name());
        }
        return b.build();
    }
}
