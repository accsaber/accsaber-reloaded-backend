package com.accsaber.backend.model.dto.response.market;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.item.UserItemResponse;
import com.accsaber.backend.model.entity.market.MarketListingStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MarketListingResponse {

    private UUID id;
    private String title;
    private MarketUserRef seller;
    private UserItemResponse item;
    private long quantity;
    private Long startingBid;
    private Long buyoutPrice;
    private long minIncrement;
    private Long currentBid;
    private MarketUserRef currentBidder;
    private Long minimumNextBid;
    private long bidCount;
    private MarketListingStatus status;
    private Instant createdAt;
    private Instant endsAt;
    private Instant settledAt;
    private MarketUserRef winner;
    private Long finalPrice;
}
