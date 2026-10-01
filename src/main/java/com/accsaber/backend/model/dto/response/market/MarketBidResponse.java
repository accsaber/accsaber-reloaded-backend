package com.accsaber.backend.model.dto.response.market;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.PlayerRef;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MarketBidResponse {

    private UUID id;
    private UUID listingId;
    private PlayerRef bidder;
    private long amount;
    private boolean buyout;
    private Instant createdAt;
}
