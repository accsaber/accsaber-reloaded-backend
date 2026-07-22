package com.accsaber.backend.service.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import com.accsaber.backend.model.entity.item.EssenceReason;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.model.entity.market.MarketListing;
import com.accsaber.backend.model.entity.market.MarketListingStatus;
import com.accsaber.backend.model.entity.notification.NotificationType;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.market.MarketListingRepository;
import com.accsaber.backend.service.item.EssenceLedgerService;
import com.accsaber.backend.service.item.ItemTransferService;
import com.accsaber.backend.service.notification.NotificationService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketSettlementServiceTest {

    private static final UUID LISTING_ID = UUID.randomUUID();
    private static final Long SELLER_ID = 1L;
    private static final Long WINNER_ID = 2L;

    @Mock
    private MarketListingRepository listingRepository;
    @Mock
    private ItemTransferService itemTransferService;
    @Mock
    private EssenceLedgerService essenceLedgerService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MarketSettlementService settlementService;

    @Test
    void anAuctionWithNoBidsReturnsTheItemToTheSeller() {
        MarketListing listing = listing(null, null);
        stubClaim(listing);

        settlementService.settleNextDue();

        verify(itemTransferService).releaseEscrow(listing.getUserItemLink());
        verify(essenceLedgerService, never()).settleReserved(anyLong(), anyLong(), anyLong(), any(), any(), any());
        assertThat(listing.getStatus()).isEqualTo(MarketListingStatus.expired);
        assertThat(listing.getSettledAt()).isNotNull();
    }

    @Test
    void anAuctionWithABidPaysTheSellerAndHandsOverTheItem() {
        MarketListing listing = listing(user(WINNER_ID), 250L);
        stubClaim(listing);

        settlementService.settleNextDue();

        verify(essenceLedgerService).settleReserved(WINNER_ID, SELLER_ID, 250L,
                EssenceReason.purchase, EssenceReason.sale, LISTING_ID);
        verify(itemTransferService).transferEscrowed(listing.getUserItemLink(), WINNER_ID,
                ItemSource.market, "Purchased on the market");
        assertThat(listing.getStatus()).isEqualTo(MarketListingStatus.sold);
        assertThat(listing.getFinalPrice()).isEqualTo(250L);
        assertThat(listing.getWinner().getId()).isEqualTo(WINNER_ID);
    }

    @Test
    void theWinnerOfAnAuctionIsNotifiedTheyWon() {
        MarketListing listing = listing(user(WINNER_ID), 250L);
        stubClaim(listing);

        settlementService.settleNextDue();

        verify(notificationService).notify(WINNER_ID, NotificationType.market_won, SELLER_ID,
                "You won A thing for 250 essence", "/market/" + LISTING_ID);
    }

    @Test
    void aBuyoutDoesNotNotifyTheBuyerTheyWon() {
        MarketListing listing = listing(null, null);

        settlementService.award(listing, user(WINNER_ID), 500L);

        verify(notificationService, never()).notify(anyLong(), eq(NotificationType.market_won),
                any(), any(), any());
        verify(notificationService).notify(eq(SELLER_ID), eq(NotificationType.market_sold),
                eq(WINNER_ID), any(), any());
    }

    @Test
    void settlementIsIdempotentForAlreadySettledListings() {
        MarketListing listing = listing(user(WINNER_ID), 250L);
        listing.setStatus(MarketListingStatus.sold);
        stubClaim(listing);

        settlementService.settleNextDue();

        verify(essenceLedgerService, never()).settleReserved(anyLong(), anyLong(), anyLong(), any(), any(), any());
        verify(itemTransferService, never()).transferEscrowed(any(), anyLong(), any(), any());
    }

    @Test
    void closingAListingWithAStandingBidRefundsThatBidder() {
        MarketListing listing = listing(user(WINNER_ID), 250L);

        settlementService.close(listing, MarketListingStatus.cancelled);

        verify(essenceLedgerService).release(WINNER_ID, 250L);
        verify(itemTransferService).releaseEscrow(listing.getUserItemLink());
        assertThat(listing.getCurrentBidder()).isNull();
        assertThat(listing.getStatus()).isEqualTo(MarketListingStatus.cancelled);
    }

    @Test
    void nothingDueMeansNothingSettled() {
        when(listingRepository.claimDueForSettlement(any(Instant.class), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());

        assertThat(settlementService.settleNextDue()).isFalse();
    }

    private void stubClaim(MarketListing listing) {
        when(listingRepository.claimDueForSettlement(any(Instant.class), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(LISTING_ID));
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));
    }

    private static MarketListing listing(User currentBidder, Long currentBid) {
        Item item = Item.builder()
                .id(UUID.randomUUID())
                .type(ItemType.builder().key("title").name("Title").build())
                .name("A thing")
                .stackable(false)
                .tradeable(true)
                .build();
        return MarketListing.builder()
                .id(LISTING_ID)
                .seller(user(SELLER_ID))
                .item(item)
                .userItemLink(UserItemLink.builder()
                        .id(UUID.randomUUID())
                        .user(user(SELLER_ID))
                        .item(item)
                        .quantity(1L)
                        .escrowed(true)
                        .build())
                .title("A thing")
                .quantity(1L)
                .startingBid(100L)
                .minIncrement(1L)
                .currentBid(currentBid)
                .currentBidder(currentBidder)
                .status(MarketListingStatus.active)
                .endsAt(Instant.now().minusSeconds(1))
                .build();
    }

    private static User user(Long id) {
        return User.builder().id(id).name("player" + id).build();
    }
}
