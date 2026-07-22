package com.accsaber.backend.service.market;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.market.CreateListingRequest;
import com.accsaber.backend.model.dto.request.market.MarketFilter;
import com.accsaber.backend.model.dto.response.market.MarketListingResponse;
import com.accsaber.backend.model.entity.item.TradeStatus;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.model.entity.market.MarketListing;
import com.accsaber.backend.model.entity.market.MarketListingStatus;
import com.accsaber.backend.repository.item.UserItemLinkRepository;
import com.accsaber.backend.repository.item.UserItemTradeItemRepository;
import com.accsaber.backend.repository.market.MarketBidRepository;
import com.accsaber.backend.repository.market.MarketListingRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.item.ItemService;
import com.accsaber.backend.service.item.ItemTransferService;
import com.accsaber.backend.service.player.DuplicateUserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketListingService {

    private final MarketListingRepository listingRepository;
    private final MarketBidRepository bidRepository;
    private final UserItemLinkRepository userItemLinkRepository;
    private final UserItemTradeItemRepository tradeItemRepository;
    private final UserRepository userRepository;
    private final DuplicateUserService duplicateUserService;
    private final ItemTransferService itemTransferService;
    private final ItemService itemService;
    private final MarketSettlementService settlementService;

    @Value("${accsaber.market.max-active-listings:20}")
    private int maxActiveListings;

    @Value("${accsaber.market.min-duration-minutes:30}")
    private int minDurationMinutes;

    @Value("${accsaber.market.max-duration-minutes:10080}")
    private int maxDurationMinutes;

    @Transactional
    public MarketListingResponse create(Long userId, CreateListingRequest req) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        validatePricing(req);
        Instant endsAt = resolveEndsAt(req);

        if (listingRepository.countBySeller_IdAndStatus(resolved, MarketListingStatus.active) >= maxActiveListings) {
            throw new ConflictException("You already have " + maxActiveListings + " active listings");
        }

        UserItemLink link = userItemLinkRepository.findByIdForUpdate(req.getUserItemLinkId())
                .orElseThrow(() -> new ResourceNotFoundException("UserItemLink", req.getUserItemLinkId()));
        validateListable(link, resolved, req.getQuantity());

        UserItemLink escrowed = itemTransferService.escrow(link, req.getQuantity());

        MarketListing saved = listingRepository.save(MarketListing.builder()
                .seller(userRepository.getReferenceById(resolved))
                .item(escrowed.getItem())
                .userItemLink(escrowed)
                .title(req.getTitle())
                .description(req.getDescription())
                .quantity(req.getQuantity())
                .startingBid(req.getStartingBid())
                .buyoutPrice(req.getBuyoutPrice())
                .minIncrement(req.getMinIncrement())
                .endsAt(endsAt)
                .build());
        return MarketMapper.toListingResponse(saved, 0L);
    }

    @Transactional
    public MarketListingResponse cancel(UUID listingId, Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        MarketListing listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("MarketListing", listingId));
        if (!listing.getSeller().getId().equals(resolved)) {
            throw new ValidationException("listingId", "only the seller can cancel this listing");
        }
        if (listing.getStatus() != MarketListingStatus.active) {
            throw new ValidationException("listingId",
                    "listing is no longer active (status: " + listing.getStatus() + ")");
        }
        if (listing.getCurrentBidder() != null) {
            throw new ConflictException("This listing already has bids and can no longer be cancelled");
        }
        settlementService.close(listing, MarketListingStatus.cancelled);
        return MarketMapper.toListingResponse(listing, bidRepository.countByListing_Id(listingId));
    }

    public Page<MarketListingResponse> browse(MarketFilter filter, Pageable pageable) {
        MarketFilter f = filter == null ? MarketFilter.empty() : filter;
        Page<MarketListing> page = listingRepository.browse(
                f.statusOrActive(),
                f.sellerId(),
                f.typeKeysOrNull(),
                f.raritiesOrNull(),
                f.modifierKeysOrNull(),
                f.effectKeysOrNull(),
                f.auctionsOnly(),
                f.buyoutOnly(),
                f.minPrice(),
                f.maxPrice(),
                f.searchOrNull(),
                MarketSort.apply(f.sort(), pageable));
        return withBidCounts(page);
    }

    public Page<MarketListingResponse> findInvolvingUser(Long userId, Collection<MarketListingStatus> statuses,
            Pageable pageable) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        Collection<MarketListingStatus> effective = (statuses == null || statuses.isEmpty())
                ? List.of(MarketListingStatus.values())
                : statuses;
        return withBidCounts(listingRepository.findInvolvingUser(resolved, effective, pageable));
    }

    public MarketListingResponse findDetail(UUID listingId) {
        MarketListing listing = listingRepository.findByIdHydrated(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("MarketListing", listingId));
        return MarketMapper.toListingResponse(listing, bidRepository.countByListing_Id(listingId));
    }

    private Page<MarketListingResponse> withBidCounts(Page<MarketListing> page) {
        if (page.isEmpty()) {
            return page.map(l -> MarketMapper.toListingResponse(l, 0L));
        }
        List<UUID> ids = page.getContent().stream().map(MarketListing::getId).toList();
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : bidRepository.countByListingIds(ids)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return page.map(l -> MarketMapper.toListingResponse(l, counts.getOrDefault(l.getId(), 0L)));
    }

    private void validatePricing(CreateListingRequest req) {
        if (req.getStartingBid() == null && req.getBuyoutPrice() == null) {
            throw new ValidationException("startingBid", "a listing needs a starting bid, a buyout price, or both");
        }
        if (req.getStartingBid() != null && req.getBuyoutPrice() != null
                && req.getBuyoutPrice() < req.getStartingBid()) {
            throw new ValidationException("buyoutPrice", "buyout price cannot be below the starting bid");
        }
    }

    private Instant resolveEndsAt(CreateListingRequest req) {
        if (req.getDurationMinutes() == null) {
            if (req.getStartingBid() != null) {
                throw new ValidationException("durationMinutes",
                        "an auction must have a duration; only buy-now listings can run indefinitely");
            }
            return null;
        }
        int minutes = req.getDurationMinutes();
        if (minutes < minDurationMinutes || minutes > maxDurationMinutes) {
            throw new ValidationException("durationMinutes",
                    "duration must be between " + minDurationMinutes + " and " + maxDurationMinutes + " minutes");
        }
        return Instant.now().plus(minutes, ChronoUnit.MINUTES);
    }

    private void validateListable(UserItemLink link, Long ownerId, long quantity) {
        if (!link.getUser().getId().equals(ownerId)) {
            throw new ValidationException("userItemLinkId", "you do not own this item");
        }
        if (link.isEscrowed()) {
            throw new ConflictException("This item is already listed on the market");
        }
        if (!link.getItem().isTradeable()) {
            throw new ValidationException("userItemLinkId", "this item is not tradeable and cannot be listed");
        }
        if (link.getItem().isDeprecated()) {
            throw new ValidationException("userItemLinkId", "this item is deprecated and cannot be listed");
        }
        if (quantity > link.getQuantity()) {
            throw new ValidationException("quantity", "you do not own that many of this item");
        }
        if (!link.getItem().isStackable() && quantity != 1) {
            throw new ValidationException("quantity", "this item is non-stackable; quantity must be 1");
        }
        if (!tradeItemRepository.findLinkIdsInTradesWithStatus(List.of(link.getId()), TradeStatus.pending).isEmpty()) {
            throw new ConflictException("This item is part of a pending trade and cannot be listed");
        }
        if (itemService.isLinkEquipped(ownerId, link.getId(), link.getItem().getType().getKey())) {
            throw new ConflictException("Unequip this item before listing it");
        }
    }
}
