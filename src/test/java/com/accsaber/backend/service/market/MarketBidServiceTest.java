package com.accsaber.backend.service.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.accsaber.backend.exception.TooManyRequestsException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.market.MarketListing;
import com.accsaber.backend.model.entity.market.MarketListingStatus;
import com.accsaber.backend.model.entity.notification.NotificationType;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.market.MarketBidRepository;
import com.accsaber.backend.repository.market.MarketListingRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.item.EssenceLedgerService;
import com.accsaber.backend.service.notification.NotificationService;
import com.accsaber.backend.service.player.DuplicateUserService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketBidServiceTest {

    private static final UUID LISTING_ID = UUID.randomUUID();
    private static final Long SELLER_ID = 1L;
    private static final Long BIDDER_ID = 2L;
    private static final Long RIVAL_ID = 3L;

    @Mock
    private MarketListingRepository listingRepository;
    @Mock
    private MarketBidRepository bidRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DuplicateUserService duplicateUserService;
    @Mock
    private EssenceLedgerService essenceLedgerService;
    @Mock
    private MarketSettlementService settlementService;
    @Mock
    private MarketBidRateLimitService rateLimitService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MarketBidService bidService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(bidService, "antiSnipeSeconds", 60);
        when(rateLimitService.tryAcquire(any())).thenReturn(true);
        when(duplicateUserService.resolvePrimaryUserId(BIDDER_ID)).thenReturn(BIDDER_ID);
        when(duplicateUserService.resolvePrimaryUserId(SELLER_ID)).thenReturn(SELLER_ID);
        when(userRepository.findById(BIDDER_ID)).thenReturn(Optional.of(user(BIDDER_ID)));
        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(user(SELLER_ID)));
    }

    @Test
    void firstBidReservesTheBidAmount() {
        stubListing(auction(100L, null, null, null));

        bidService.placeBid(LISTING_ID, BIDDER_ID, 100L);

        verify(essenceLedgerService).reserve(BIDDER_ID, 100L);
        verify(essenceLedgerService, never()).release(anyLong(), anyLong());
    }

    @Test
    void outbiddingRefundsThePreviousBidder() {
        stubListing(auction(100L, null, 100L, user(RIVAL_ID)));

        bidService.placeBid(LISTING_ID, BIDDER_ID, 150L);

        verify(essenceLedgerService).release(RIVAL_ID, 100L);
        verify(essenceLedgerService).reserve(BIDDER_ID, 150L);
    }

    @Test
    void theDisplacedBidderIsNotifiedTheyWereOutbid() {
        stubListing(auction(100L, null, 100L, user(RIVAL_ID)));

        bidService.placeBid(LISTING_ID, BIDDER_ID, 150L);

        verify(notificationService).notify(RIVAL_ID, NotificationType.market_outbid, BIDDER_ID,
                "You were outbid on A thing", "/market/" + LISTING_ID);
    }

    @Test
    void aBuyoutAlsoNotifiesTheStandingBidderTheyLost() {
        stubListing(auction(100L, 500L, 200L, user(RIVAL_ID)));

        bidService.buyNow(LISTING_ID, BIDDER_ID);

        verify(notificationService).notify(RIVAL_ID, NotificationType.market_outbid, BIDDER_ID,
                "You were outbid on A thing", "/market/" + LISTING_ID);
    }

    @Test
    void theFirstBidOnAListingNotifiesNobodyAboutBeingOutbid() {
        stubListing(auction(100L, null, null, null));

        bidService.placeBid(LISTING_ID, BIDDER_ID, 100L);

        verify(notificationService, never()).notify(anyLong(), eq(NotificationType.market_outbid),
                any(), any(), any());
    }

    @Test
    void holdsAreAppliedInAscendingUserIdOrderToAvoidDeadlocks() {
        stubListing(auction(100L, null, 100L, user(RIVAL_ID)));
        bidService.placeBid(LISTING_ID, BIDDER_ID, 150L);

        InOrder lowerBidderFirst = Mockito.inOrder(essenceLedgerService);
        lowerBidderFirst.verify(essenceLedgerService).reserve(BIDDER_ID, 150L);
        lowerBidderFirst.verify(essenceLedgerService).release(RIVAL_ID, 100L);

        Mockito.reset(essenceLedgerService);
        when(duplicateUserService.resolvePrimaryUserId(RIVAL_ID)).thenReturn(RIVAL_ID);
        when(userRepository.findById(RIVAL_ID)).thenReturn(Optional.of(user(RIVAL_ID)));
        stubListing(auction(100L, null, 100L, user(BIDDER_ID)));

        bidService.placeBid(LISTING_ID, RIVAL_ID, 150L);

        InOrder lowerHolderFirst = Mockito.inOrder(essenceLedgerService);
        lowerHolderFirst.verify(essenceLedgerService).release(BIDDER_ID, 100L);
        lowerHolderFirst.verify(essenceLedgerService).reserve(RIVAL_ID, 150L);
    }

    @Test
    void bidBelowTheMinimumIncrementIsRejected() {
        MarketListing listing = auction(100L, null, 100L, user(RIVAL_ID));
        listing.setMinIncrement(10L);
        stubListing(listing);

        assertThatThrownBy(() -> bidService.placeBid(LISTING_ID, BIDDER_ID, 105L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("at least 110");

        verify(essenceLedgerService, never()).reserve(anyLong(), anyLong());
    }

    @Test
    void sellerCannotBidOnTheirOwnListing() {
        stubListing(auction(100L, null, null, null));

        assertThatThrownBy(() -> bidService.placeBid(LISTING_ID, SELLER_ID, 100L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("your own listing");
    }

    @Test
    void currentHighBidderCannotBidAgainstThemselves() {
        stubListing(auction(100L, null, 100L, user(BIDDER_ID)));

        assertThatThrownBy(() -> bidService.placeBid(LISTING_ID, BIDDER_ID, 200L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already the highest bidder");
    }

    @Test
    void bidAtOrAboveBuyoutCompletesThePurchase() {
        MarketListing listing = auction(100L, 500L, null, null);
        stubListing(listing);

        bidService.placeBid(LISTING_ID, BIDDER_ID, 500L);

        verify(essenceLedgerService).reserve(BIDDER_ID, 500L);
        verify(settlementService).award(any(MarketListing.class), any(User.class), anyLong());
    }

    @Test
    void bidInTheFinalSecondsExtendsTheAuction() {
        MarketListing listing = auction(100L, null, null, null);
        Instant originalEnd = Instant.now().plusSeconds(5);
        listing.setEndsAt(originalEnd);
        stubListing(listing);

        bidService.placeBid(LISTING_ID, BIDDER_ID, 100L);

        assertThat(listing.getEndsAt()).isAfter(originalEnd);
    }

    @Test
    void bidsOnAnEndedListingAreRejected() {
        MarketListing listing = auction(100L, null, null, null);
        listing.setEndsAt(Instant.now().minusSeconds(1));
        stubListing(listing);

        assertThatThrownBy(() -> bidService.placeBid(LISTING_ID, BIDDER_ID, 100L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already ended");
    }

    @Test
    void anEndlessBuyNowListingCanStillBeBought() {
        MarketListing listing = auction(null, 500L, null, null);
        listing.setEndsAt(null);
        stubListing(listing);

        bidService.buyNow(LISTING_ID, BIDDER_ID);

        verify(essenceLedgerService).reserve(BIDDER_ID, 500L);
        verify(settlementService).award(any(MarketListing.class), any(User.class), anyLong());
    }

    @Test
    void anEndlessListingIsNeverConsideredEnded() {
        MarketListing listing = auction(null, 500L, null, null);
        listing.setEndsAt(null);
        stubListing(listing);

        assertThatThrownBy(() -> bidService.placeBid(LISTING_ID, BIDDER_ID, 100L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("buy-now only");
    }

    @Test
    void shopListingsRejectBidsBelowTheBuyoutPrice() {
        stubListing(auction(null, 500L, null, null));

        assertThatThrownBy(() -> bidService.placeBid(LISTING_ID, BIDDER_ID, 100L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("buy-now only");
    }

    @Test
    void rateLimitedBiddersAreRejected() {
        when(rateLimitService.tryAcquire(BIDDER_ID)).thenReturn(false);

        assertThatThrownBy(() -> bidService.placeBid(LISTING_ID, BIDDER_ID, 100L))
                .isInstanceOf(TooManyRequestsException.class);
    }

    private void stubListing(MarketListing listing) {
        when(listingRepository.findByIdForUpdate(LISTING_ID)).thenReturn(Optional.of(listing));
    }

    private static MarketListing auction(Long startingBid, Long buyout, Long currentBid, User currentBidder) {
        return MarketListing.builder()
                .id(LISTING_ID)
                .seller(user(SELLER_ID))
                .title("A thing")
                .quantity(1L)
                .startingBid(startingBid)
                .buyoutPrice(buyout)
                .minIncrement(1L)
                .currentBid(currentBid)
                .currentBidder(currentBidder)
                .status(MarketListingStatus.active)
                .endsAt(Instant.now().plus(2, ChronoUnit.DAYS))
                .build();
    }

    private static User user(Long id) {
        return User.builder().id(id).name("player" + id).build();
    }
}
