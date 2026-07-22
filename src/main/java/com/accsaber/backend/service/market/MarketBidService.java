package com.accsaber.backend.service.market;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.TooManyRequestsException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.market.MarketBidResponse;
import com.accsaber.backend.model.entity.market.MarketBid;
import com.accsaber.backend.model.entity.market.MarketListing;
import com.accsaber.backend.model.entity.market.MarketListingStatus;
import com.accsaber.backend.model.entity.notification.NotificationType;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.MarketListingEvent;
import com.accsaber.backend.repository.market.MarketBidRepository;
import com.accsaber.backend.repository.market.MarketListingRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.item.EssenceLedgerService;
import com.accsaber.backend.service.notification.NotificationService;
import com.accsaber.backend.service.player.DuplicateUserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketBidService {

    private final MarketListingRepository listingRepository;
    private final MarketBidRepository bidRepository;
    private final UserRepository userRepository;
    private final DuplicateUserService duplicateUserService;
    private final EssenceLedgerService essenceLedgerService;
    private final MarketSettlementService settlementService;
    private final MarketBidRateLimitService rateLimitService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${accsaber.market.anti-snipe-seconds:60}")
    private int antiSnipeSeconds;

    public List<MarketBidResponse> findBids(UUID listingId) {
        return bidRepository.findByListingHydrated(listingId).stream()
                .map(MarketMapper::toBidResponse)
                .toList();
    }

    public Page<MarketBidResponse> findMyBids(Long userId, Pageable pageable) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        return bidRepository.findByBidderHydrated(resolved, pageable).map(MarketMapper::toBidResponse);
    }

    @Transactional
    public MarketListing placeBid(UUID listingId, Long userId, long amount) {
        User bidder = requireBidder(userId);
        MarketListing listing = lockActive(listingId, bidder.getId());

        if (listing.getBuyoutPrice() != null && amount >= listing.getBuyoutPrice()) {
            return executeBuyout(listing, bidder);
        }
        if (!listing.isAuction()) {
            throw new ValidationException("amount", "this listing is buy-now only");
        }
        if (listing.getCurrentBidder() != null && listing.getCurrentBidder().getId().equals(bidder.getId())) {
            throw new ValidationException("amount", "you are already the highest bidder");
        }
        long minimum = listing.minimumAcceptableBid();
        if (amount < minimum) {
            throw new ValidationException("amount", "bid must be at least " + minimum + " essence");
        }

        rebalanceHolds(listing, bidder.getId(), amount);
        listing.setCurrentBid(amount);
        listing.setCurrentBidder(bidder);

        Instant previousEnd = listing.getEndsAt();
        extendIfSniping(listing);
        listingRepository.save(listing);
        bidRepository.save(MarketBid.builder()
                .listing(listing)
                .bidder(bidder)
                .amount(amount)
                .buyout(false)
                .build());

        notificationService.notify(listing.getSeller().getId(), NotificationType.market_bid, bidder.getId(),
                "New bid of " + amount + " essence on " + listing.getTitle(),
                "/market/" + listing.getId());

        publish(listing, "bid", amount, bidder);
        if (!Objects.equals(listing.getEndsAt(), previousEnd)) {
            publish(listing, "extended", null, bidder);
        }
        return listing;
    }

    @Transactional
    public MarketListing buyNow(UUID listingId, Long userId) {
        User buyer = requireBidder(userId);
        MarketListing listing = lockActive(listingId, buyer.getId());
        if (listing.getBuyoutPrice() == null) {
            throw new ValidationException("listingId", "this listing has no buyout price");
        }
        return executeBuyout(listing, buyer);
    }

    private MarketListing executeBuyout(MarketListing listing, User buyer) {
        long price = listing.getBuyoutPrice();
        rebalanceHolds(listing, buyer.getId(), price);
        listing.setCurrentBid(price);
        listing.setCurrentBidder(buyer);
        bidRepository.save(MarketBid.builder()
                .listing(listing)
                .bidder(buyer)
                .amount(price)
                .buyout(true)
                .build());
        settlementService.award(listing, buyer, price);
        return listing;
    }

    private void rebalanceHolds(MarketListing listing, Long bidderId, long amount) {
        User previous = listing.getCurrentBidder();
        if (previous == null) {
            essenceLedgerService.reserve(bidderId, amount);
            return;
        }
        Long previousId = previous.getId();
        long previousAmount = listing.getCurrentBid();
        if (previousId.equals(bidderId)) {
            essenceLedgerService.release(previousId, previousAmount);
            essenceLedgerService.reserve(bidderId, amount);
            return;
        }
        if (previousId < bidderId) {
            essenceLedgerService.release(previousId, previousAmount);
            essenceLedgerService.reserve(bidderId, amount);
        } else {
            essenceLedgerService.reserve(bidderId, amount);
            essenceLedgerService.release(previousId, previousAmount);
        }
    }

    private void extendIfSniping(MarketListing listing) {
        if (antiSnipeSeconds <= 0 || listing.isEndless()) {
            return;
        }
        Instant threshold = Instant.now().plusSeconds(antiSnipeSeconds);
        if (listing.getEndsAt().isBefore(threshold)) {
            listing.setEndsAt(threshold);
        }
    }

    private User requireBidder(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        if (!rateLimitService.tryAcquire(resolved)) {
            throw new TooManyRequestsException("You are bidding too quickly. Try again in a moment.");
        }
        return userRepository.findById(resolved)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private MarketListing lockActive(UUID listingId, Long actorId) {
        MarketListing listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("MarketListing", listingId));
        if (listing.getStatus() != MarketListingStatus.active) {
            throw new ValidationException("listingId",
                    "listing is no longer active (status: " + listing.getStatus() + ")");
        }
        if (!listing.isEndless() && !listing.getEndsAt().isAfter(Instant.now())) {
            throw new ValidationException("listingId", "this listing has already ended");
        }
        if (listing.getSeller().getId().equals(actorId)) {
            throw new ValidationException("listingId", "you cannot bid on your own listing");
        }
        return listing;
    }

    private void publish(MarketListing listing, String type, Long amount, User actor) {
        eventPublisher.publishEvent(new MarketListingEvent(listing.getId(), type, listing.getStatus(),
                amount, MarketMapper.toUserRef(actor), listing.getEndsAt()));
    }
}
