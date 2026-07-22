package com.accsaber.backend.service.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.market.CreateListingRequest;
import com.accsaber.backend.model.dto.response.market.MarketListingResponse;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.model.entity.market.MarketListingStatus;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.item.UserItemLinkRepository;
import com.accsaber.backend.repository.item.UserItemTradeItemRepository;
import com.accsaber.backend.repository.market.MarketBidRepository;
import com.accsaber.backend.repository.market.MarketListingRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.item.ItemService;
import com.accsaber.backend.service.item.ItemTransferService;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.supporter.SupporterService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketListingCreateTest {

    private static final Long SELLER_ID = 1L;
    private static final UUID LINK_ID = UUID.randomUUID();

    @Mock
    private MarketListingRepository listingRepository;
    @Mock
    private MarketBidRepository bidRepository;
    @Mock
    private UserItemLinkRepository userItemLinkRepository;
    @Mock
    private UserItemTradeItemRepository tradeItemRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DuplicateUserService duplicateUserService;
    @Mock
    private ItemTransferService itemTransferService;
    @Mock
    private ItemService itemService;
    @Mock
    private MarketSettlementService settlementService;
    @Mock
    private SupporterService supporterService;

    @InjectMocks
    private MarketListingService listingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(listingService, "maxActiveListings", 20);
        ReflectionTestUtils.setField(listingService, "minDurationMinutes", 30);
        ReflectionTestUtils.setField(listingService, "maxDurationMinutes", 10080);

        UserItemLink link = link();
        when(duplicateUserService.resolvePrimaryUserId(SELLER_ID)).thenReturn(SELLER_ID);
        when(userItemLinkRepository.findByIdForUpdate(LINK_ID)).thenReturn(Optional.of(link));
        when(listingRepository.countBySeller_IdAndStatus(SELLER_ID, MarketListingStatus.active)).thenReturn(0L);
        when(tradeItemRepository.findLinkIdsInTradesWithStatus(any(), any())).thenReturn(List.of());
        when(itemService.isLinkEquipped(anyLong(), any(), any())).thenReturn(false);
        when(itemTransferService.escrow(any(), anyLong())).thenReturn(link);
        when(userRepository.getReferenceById(SELLER_ID)).thenReturn(user());
        when(listingRepository.save(any())).thenAnswer(returnsFirstArg());
    }

    @Test
    void aBuyNowOnlyListingWithNoDurationRunsIndefinitely() {
        CreateListingRequest req = request(null, 500L, null);

        MarketListingResponse res = listingService.create(SELLER_ID, req);

        assertThat(res.getEndsAt()).isNull();
        assertThat(res.getStartingBid()).isNull();
        assertThat(res.getBuyoutPrice()).isEqualTo(500L);
    }

    @Test
    void aBuyNowOnlyListingCanStillOptIntoADuration() {
        CreateListingRequest req = request(null, 500L, 60);

        MarketListingResponse res = listingService.create(SELLER_ID, req);

        assertThat(res.getEndsAt()).isNotNull();
    }

    @Test
    void theCreateResponseIsFullyMaterialisedInsideTheTransaction() {
        MarketListingResponse res = listingService.create(SELLER_ID, request(null, 500L, null));

        assertThat(res.getSeller()).isNotNull();
        assertThat(res.getSeller().getName()).isEqualTo("seller");
        assertThat(res.getItem()).isNotNull();
        assertThat(res.getItem().getItem().getName()).isEqualTo("A thing");
    }

    @Test
    void anAuctionWithoutADurationIsRejected() {
        CreateListingRequest req = request(100L, null, null);

        assertThatThrownBy(() -> listingService.create(SELLER_ID, req))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("only buy-now listings can run indefinitely");
    }

    @Test
    void anAuctionWithABuyoutStillRequiresADuration() {
        CreateListingRequest req = request(100L, 500L, null);

        assertThatThrownBy(() -> listingService.create(SELLER_ID, req))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("only buy-now listings can run indefinitely");
    }

    @Test
    void durationOutsideTheAllowedRangeIsRejected() {
        assertThatThrownBy(() -> listingService.create(SELLER_ID, request(100L, null, 5)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("between 30 and 10080");

        assertThatThrownBy(() -> listingService.create(SELLER_ID, request(100L, null, 20_000)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("between 30 and 10080");
    }

    @Test
    void aNonSupporterIsCappedAtTheConfiguredListingLimit() {
        when(listingRepository.countBySeller_IdAndStatus(SELLER_ID, MarketListingStatus.active)).thenReturn(20L);

        assertThatThrownBy(() -> listingService.create(SELLER_ID, request(null, 500L, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Supporters get unlimited listings");
    }

    @Test
    void aSupporterIsNeverCapped() {
        when(supporterService.isActiveSupporter(SELLER_ID)).thenReturn(true);
        when(listingRepository.countBySeller_IdAndStatus(SELLER_ID, MarketListingStatus.active)).thenReturn(500L);

        MarketListingResponse res = listingService.create(SELLER_ID, request(null, 500L, null));

        assertThat(res).isNotNull();
    }

    @Test
    void theCapCheckIsSkippedEntirelyForSupporters() {
        when(supporterService.isActiveSupporter(SELLER_ID)).thenReturn(true);

        listingService.create(SELLER_ID, request(null, 500L, null));

        verify(listingRepository, never()).countBySeller_IdAndStatus(any(), any());
    }

    @Test
    void aListingWithNoPriceAtAllIsRejected() {
        assertThatThrownBy(() -> listingService.create(SELLER_ID, request(null, null, 60)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("starting bid, a buyout price, or both");
    }

    private static CreateListingRequest request(Long startingBid, Long buyout, Integer durationMinutes) {
        CreateListingRequest req = new CreateListingRequest();
        req.setUserItemLinkId(LINK_ID);
        req.setQuantity(1);
        req.setTitle("A thing");
        req.setStartingBid(startingBid);
        req.setBuyoutPrice(buyout);
        req.setMinIncrement(1);
        req.setDurationMinutes(durationMinutes);
        return req;
    }

    private static UserItemLink link() {
        Item item = Item.builder()
                .id(UUID.randomUUID())
                .type(ItemType.builder().key("title").name("Title").build())
                .name("A thing")
                .stackable(false)
                .tradeable(true)
                .deprecated(false)
                .build();
        return UserItemLink.builder()
                .id(LINK_ID)
                .user(user())
                .item(item)
                .modifiers(Set.of())
                .quantity(1L)
                .source(ItemSource.manual)
                .build();
    }

    private static User user() {
        return User.builder().id(SELLER_ID).name("seller").build();
    }
}
