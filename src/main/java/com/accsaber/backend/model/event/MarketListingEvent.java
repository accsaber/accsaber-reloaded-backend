package com.accsaber.backend.model.event;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.market.MarketListingStatus;

public record MarketListingEvent(
        UUID listingId,
        String type,
        MarketListingStatus status,
        Long amount,
        PlayerRef actor,
        PlayerRef seller,
        Instant endsAt) {
}
