package com.accsaber.backend.model.event;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.market.MarketUserRef;
import com.accsaber.backend.model.entity.market.MarketListingStatus;

public record MarketListingEvent(
        UUID listingId,
        String type,
        MarketListingStatus status,
        Long amount,
        MarketUserRef actor,
        Instant endsAt) {
}
