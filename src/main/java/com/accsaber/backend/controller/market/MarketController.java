package com.accsaber.backend.controller.market;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.model.dto.request.market.CreateListingRequest;
import com.accsaber.backend.model.dto.request.market.MarketFilter;
import com.accsaber.backend.model.dto.request.market.MarketKind;
import com.accsaber.backend.model.dto.request.market.MarketSortOption;
import com.accsaber.backend.model.dto.request.market.PlaceBidRequest;
import com.accsaber.backend.model.dto.response.market.MarketBidResponse;
import com.accsaber.backend.model.dto.response.market.MarketListingResponse;
import com.accsaber.backend.model.entity.item.ItemRarity;
import com.accsaber.backend.model.entity.market.MarketListingStatus;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.market.MarketBidService;
import com.accsaber.backend.service.market.MarketListingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/market")
@RequiredArgsConstructor
@Tag(name = "Market")
public class MarketController {

    private final MarketListingService listingService;
    private final MarketBidService bidService;

    @Operation(summary = "Browse market listings", description = "Public. Defaults to active listings ending soonest. kind: auction | shop. sortBy: ending_soon | newest | price_asc | price_desc. modifierKey/effectKey match the listed instance and only apply to listings whose item link still exists.")
    @GetMapping("/listings")
    public ResponseEntity<Page<MarketListingResponse>> browse(
            @RequestParam(required = false) MarketListingStatus status,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) List<String> typeKey,
            @RequestParam(required = false) List<ItemRarity> rarity,
            @RequestParam(required = false) List<String> modifierKey,
            @RequestParam(required = false) List<String> effectKey,
            @RequestParam(required = false) MarketKind kind,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) MarketSortOption sortBy,
            @PageableDefault(size = 30) Pageable pageable) {
        MarketFilter filter = new MarketFilter(status, sellerId, typeKey, rarity,
                modifierKey, effectKey, kind, minPrice, maxPrice, search, sortBy);
        return ResponseEntity.ok(listingService.browse(filter, pageable));
    }

    @Operation(summary = "Get a single listing", description = "Public. This is the shareable listing link.")
    @GetMapping("/listings/{id}")
    public ResponseEntity<MarketListingResponse> findOne(@PathVariable UUID id) {
        return ResponseEntity.ok(listingService.findDetail(id));
    }

    @Operation(summary = "Get the bid history for a listing")
    @GetMapping("/listings/{id}/bids")
    public ResponseEntity<List<MarketBidResponse>> bids(@PathVariable UUID id) {
        return ResponseEntity.ok(bidService.findBids(id));
    }

    @Operation(summary = "List an owned item on the market")
    @PostMapping("/listings")
    public ResponseEntity<MarketListingResponse> create(@Valid @RequestBody CreateListingRequest req,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long me = requirePrincipal(principal).getUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(listingService.create(me, req));
    }

    @Operation(summary = "Cancel one of my listings", description = "Only possible while the listing has no bids.")
    @DeleteMapping("/listings/{id}")
    public ResponseEntity<MarketListingResponse> cancel(@PathVariable UUID id,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long me = requirePrincipal(principal).getUserId();
        return ResponseEntity.ok(listingService.cancel(id, me));
    }

    @Operation(summary = "Place a bid", description = "A bid at or above the buyout price completes the purchase immediately.")
    @PostMapping("/listings/{id}/bids")
    public ResponseEntity<MarketListingResponse> placeBid(@PathVariable UUID id,
            @Valid @RequestBody PlaceBidRequest req,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long me = requirePrincipal(principal).getUserId();
        bidService.placeBid(id, me, req.getAmount());
        return ResponseEntity.ok(listingService.findDetail(id));
    }

    @Operation(summary = "Buy a listing outright at its buyout price")
    @PostMapping("/listings/{id}/buy")
    public ResponseEntity<MarketListingResponse> buyNow(@PathVariable UUID id,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long me = requirePrincipal(principal).getUserId();
        bidService.buyNow(id, me);
        return ResponseEntity.ok(listingService.findDetail(id));
    }

    @Operation(summary = "List market activity I am involved in", description = "Listings where I am the seller, the current high bidder, or the winner.")
    @GetMapping("/me/listings")
    public ResponseEntity<Page<MarketListingResponse>> myListings(
            @RequestParam(required = false) List<MarketListingStatus> status,
            @PageableDefault(size = 30) Pageable pageable,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long me = requirePrincipal(principal).getUserId();
        return ResponseEntity.ok(listingService.findInvolvingUser(me, status, pageable));
    }

    @Operation(summary = "List my bid history")
    @GetMapping("/me/bids")
    public ResponseEntity<Page<MarketBidResponse>> myBids(
            @PageableDefault(size = 30) Pageable pageable,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long me = requirePrincipal(principal).getUserId();
        return ResponseEntity.ok(bidService.findMyBids(me, pageable));
    }

    private PlayerUserDetails requirePrincipal(PlayerUserDetails principal) {
        if (principal == null) {
            throw new UnauthorizedException("Player authentication required");
        }
        return principal;
    }
}
