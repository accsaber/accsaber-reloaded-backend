package com.accsaber.backend.service.market;

import java.util.Map;

import com.accsaber.backend.model.dto.response.item.UserItemResponse;
import com.accsaber.backend.model.dto.response.market.MarketBidResponse;
import com.accsaber.backend.model.dto.response.market.MarketListingResponse;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.model.entity.market.MarketBid;
import com.accsaber.backend.model.entity.market.MarketListing;
import com.accsaber.backend.model.entity.market.MarketListingStatus;
import com.accsaber.backend.service.clan.ClanRefCache;
import com.accsaber.backend.service.item.ItemMapper;

public final class MarketMapper {

    private MarketMapper() {
    }

    public static MarketListingResponse toListingResponse(MarketListing listing, long bidCount,
            Map<String, Long> counters) {
        return MarketListingResponse.builder()
                .id(listing.getId())
                .title(listing.getTitle())
                .description(listing.getDescription())
                .seller(ClanRefCache.playerRef(listing.getSeller()))
                .item(toItemView(listing, counters))
                .quantity(listing.getQuantity())
                .startingBid(listing.getStartingBid())
                .buyoutPrice(listing.getBuyoutPrice())
                .minIncrement(listing.getMinIncrement())
                .currentBid(listing.getCurrentBid())
                .currentBidder(ClanRefCache.playerRef(listing.getCurrentBidder()))
                .minimumNextBid(listing.getStatus() == MarketListingStatus.active && listing.isAuction()
                        ? listing.minimumAcceptableBid()
                        : null)
                .bidCount(bidCount)
                .status(listing.getStatus())
                .createdAt(listing.getCreatedAt())
                .endsAt(listing.getEndsAt())
                .settledAt(listing.getSettledAt())
                .winner(ClanRefCache.playerRef(listing.getWinner()))
                .finalPrice(listing.getFinalPrice())
                .build();
    }

    private static UserItemResponse toItemView(MarketListing listing, Map<String, Long> counters) {
        UserItemLink link = listing.getUserItemLink();
        if (link != null) {
            return ItemMapper.toUserItemResponse(link, counters);
        }
        return UserItemResponse.builder()
                .item(ItemMapper.toItemResponse(listing.getItem()))
                .quantity(listing.getQuantity())
                .build();
    }

    public static MarketBidResponse toBidResponse(MarketBid bid) {
        return MarketBidResponse.builder()
                .id(bid.getId())
                .listingId(bid.getListing().getId())
                .bidder(ClanRefCache.playerRef(bid.getBidder()))
                .amount(bid.getAmount())
                .buyout(bid.isBuyout())
                .createdAt(bid.getCreatedAt())
                .build();
    }
}
