package com.accsaber.backend.service.market;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.entity.item.EssenceReason;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.market.MarketListing;
import com.accsaber.backend.model.entity.market.MarketListingStatus;
import com.accsaber.backend.model.entity.notification.NotificationType;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.MarketListingEvent;
import com.accsaber.backend.repository.market.MarketListingRepository;
import com.accsaber.backend.service.item.EssenceLedgerService;
import com.accsaber.backend.service.item.ItemTransferService;
import com.accsaber.backend.service.notification.NotificationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MarketSettlementService {

    private final MarketListingRepository listingRepository;
    private final ItemTransferService itemTransferService;
    private final EssenceLedgerService essenceLedgerService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public boolean settleNextDue() {
        List<UUID> claimed = listingRepository.claimDueForSettlement(Instant.now(), 1);
        if (claimed.isEmpty()) {
            return false;
        }
        MarketListing listing = listingRepository.findById(claimed.get(0)).orElse(null);
        if (listing == null || listing.getStatus() != MarketListingStatus.active) {
            return true;
        }
        if (listing.getCurrentBidder() == null) {
            close(listing, MarketListingStatus.expired);
        } else {
            award(listing, listing.getCurrentBidder(), listing.getCurrentBid());
        }
        return true;
    }

    @Transactional
    public void award(MarketListing listing, User winner, long price) {
        essenceLedgerService.settleReserved(winner.getId(), listing.getSeller().getId(), price,
                EssenceReason.purchase, EssenceReason.sale, listing.getId());
        itemTransferService.transferEscrowed(listing.getUserItemLink(), winner.getId(),
                ItemSource.market, "Purchased on the market");

        listing.setWinner(winner);
        listing.setFinalPrice(price);
        listing.setCurrentBid(null);
        listing.setCurrentBidder(null);
        listing.setStatus(MarketListingStatus.sold);
        listing.setSettledAt(Instant.now());
        listingRepository.save(listing);

        notificationService.notify(listing.getSeller().getId(), NotificationType.market_sold, winner.getId(),
                listing.getItem().getName() + " sold for " + price + " essence",
                "/market/" + listing.getId());
        publish(listing, "sold", price);
    }

    @Transactional
    public void close(MarketListing listing, MarketListingStatus status) {
        if (listing.getCurrentBidder() != null) {
            essenceLedgerService.release(listing.getCurrentBidder().getId(), listing.getCurrentBid());
            listing.setCurrentBid(null);
            listing.setCurrentBidder(null);
        }
        itemTransferService.releaseEscrow(listing.getUserItemLink());
        listing.setStatus(status);
        listing.setSettledAt(Instant.now());
        listingRepository.save(listing);
        publish(listing, status.name(), null);
    }

    private void publish(MarketListing listing, String type, Long amount) {
        eventPublisher.publishEvent(new MarketListingEvent(listing.getId(), type, listing.getStatus(),
                amount, MarketMapper.toUserRef(listing.getWinner()), listing.getEndsAt()));
    }
}
