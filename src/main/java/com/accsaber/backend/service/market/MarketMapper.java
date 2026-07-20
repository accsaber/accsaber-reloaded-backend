package com.accsaber.backend.service.market;

import com.accsaber.backend.model.dto.response.market.MarketBidResponse;
import com.accsaber.backend.model.dto.response.market.MarketListingResponse;
import com.accsaber.backend.model.dto.response.market.MarketUserRef;
import com.accsaber.backend.model.entity.market.MarketBid;
import com.accsaber.backend.model.entity.market.MarketListing;
import com.accsaber.backend.model.entity.market.MarketListingStatus;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.service.item.ItemMapper;

public final class MarketMapper {

    private MarketMapper() {
    }

    public static MarketListingResponse toListingResponse(MarketListing listing, long bidCount) {
        return MarketListingResponse.builder()
                .id(listing.getId())
                .title(listing.getTitle())
                .seller(toUserRef(listing.getSeller()))
                .item(ItemMapper.toUserItemResponse(listing.getUserItemLink()))
                .quantity(listing.getQuantity())
                .startingBid(listing.getStartingBid())
                .buyoutPrice(listing.getBuyoutPrice())
                .minIncrement(listing.getMinIncrement())
                .currentBid(listing.getCurrentBid())
                .currentBidder(toUserRef(listing.getCurrentBidder()))
                .minimumNextBid(listing.getStatus() == MarketListingStatus.active && listing.isAuction()
                        ? listing.minimumAcceptableBid()
                        : null)
                .bidCount(bidCount)
                .status(listing.getStatus())
                .createdAt(listing.getCreatedAt())
                .endsAt(listing.getEndsAt())
                .settledAt(listing.getSettledAt())
                .winner(toUserRef(listing.getWinner()))
                .finalPrice(listing.getFinalPrice())
                .build();
    }

    public static MarketBidResponse toBidResponse(MarketBid bid) {
        return MarketBidResponse.builder()
                .id(bid.getId())
                .listingId(bid.getListing().getId())
                .bidder(toUserRef(bid.getBidder()))
                .amount(bid.getAmount())
                .buyout(bid.isBuyout())
                .createdAt(bid.getCreatedAt())
                .build();
    }

    public static MarketUserRef toUserRef(User user) {
        if (user == null) {
            return null;
        }
        return MarketUserRef.builder()
                .id(user.getId())
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .cdnAvatarUrl(user.getCdnAvatarUrl())
                .country(user.getCountry())
                .build();
    }
}
