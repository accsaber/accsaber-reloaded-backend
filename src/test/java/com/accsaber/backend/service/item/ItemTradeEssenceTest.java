package com.accsaber.backend.service.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.item.EssenceReason;
import com.accsaber.backend.model.entity.item.TradeStatus;
import com.accsaber.backend.model.entity.item.UserItemTrade;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.item.UserItemLinkRepository;
import com.accsaber.backend.repository.item.UserItemTradeItemRepository;
import com.accsaber.backend.repository.item.UserItemTradeRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.notification.NotificationService;
import com.accsaber.backend.service.player.DuplicateUserService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ItemTradeEssenceTest {

    private static final Long SENDER = 1L;
    private static final Long RECIPIENT = 2L;
    private static final UUID TRADE_ID = UUID.randomUUID();

    @Mock
    private UserItemTradeRepository tradeRepository;
    @Mock
    private UserItemTradeItemRepository tradeItemRepository;
    @Mock
    private UserItemLinkRepository userItemLinkRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DuplicateUserService duplicateUserService;
    @Mock
    private ItemTransferService itemTransferService;
    @Mock
    private EssenceLedgerService essenceLedgerService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ItemTradeService tradeService;

    @BeforeEach
    void setUp() {
        when(duplicateUserService.resolvePrimaryUserId(SENDER)).thenReturn(SENDER);
        when(duplicateUserService.resolvePrimaryUserId(RECIPIENT)).thenReturn(RECIPIENT);
        when(userRepository.existsById(RECIPIENT)).thenReturn(true);
        when(userRepository.getReferenceById(SENDER)).thenReturn(user(SENDER));
        when(userRepository.getReferenceById(RECIPIENT)).thenReturn(user(RECIPIENT));
        when(tradeItemRepository.findLinkIdsInTradesWithStatus(any(), any())).thenReturn(List.of());
        when(tradeRepository.save(any())).thenAnswer(invocation -> {
            UserItemTrade persisted = invocation.getArgument(0);
            if (persisted.getId() == null) {
                persisted.setId(TRADE_ID);
            }
            return persisted;
        });
    }

    @Test
    void offeringEssenceReservesItUpFront() {
        UserItemTrade trade = tradeService.create(SENDER, RECIPIENT, List.of(), List.of(), 100L, 0L, null);

        verify(essenceLedgerService).reserve(SENDER, 100L);
        assertThat(trade.getOfferedEssence()).isEqualTo(100L);
    }

    @Test
    void requestingEssenceReservesNothingBecauseTheRecipientHasNotCommitted() {
        tradeService.create(SENDER, RECIPIENT, List.of(), List.of(), 0L, 100L, null);

        verify(essenceLedgerService, never()).reserve(anyLong(), anyLong());
    }

    @Test
    void essenceMayAppearOnBothSides() {
        UserItemTrade trade = tradeService.create(SENDER, RECIPIENT, List.of(), List.of(), 100L, 60L, null);

        assertThat(trade.getOfferedEssence()).isEqualTo(100L);
        assertThat(trade.getRequestedEssence()).isEqualTo(60L);
        verify(essenceLedgerService).reserve(SENDER, 100L);
    }

    @Test
    void acceptingATwoSidedEssenceTradeSettlesBothLegs() {
        stubPending(trade(100L, 60L));

        tradeService.accept(TRADE_ID, RECIPIENT);

        verify(essenceLedgerService).debit(RECIPIENT, 60L, EssenceReason.trade_payment, TRADE_ID);
        verify(essenceLedgerService).credit(SENDER, 60L, EssenceReason.trade_receipt, TRADE_ID);
        verify(essenceLedgerService).settleReserved(SENDER, RECIPIENT, 100L,
                EssenceReason.trade_payment, EssenceReason.trade_receipt, TRADE_ID);
    }

    @Test
    void anEmptyTradeIsRejected() {
        assertThatThrownBy(() -> tradeService.create(SENDER, RECIPIENT, List.of(), List.of(), 0L, 0L, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("at least one item or some essence");
    }

    @Test
    void negativeEssenceIsRejected() {
        assertThatThrownBy(() -> tradeService.create(SENDER, RECIPIENT, List.of(), List.of(), -5L, 0L, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    void acceptingSettlesOfferedEssenceFromTheSendersHold() {
        stubPending(trade(100L, 0L));

        tradeService.accept(TRADE_ID, RECIPIENT);

        verify(essenceLedgerService).settleReserved(SENDER, RECIPIENT, 100L,
                EssenceReason.trade_payment, EssenceReason.trade_receipt, TRADE_ID);
    }

    @Test
    void acceptingDebitsTheRecipientForRequestedEssence() {
        stubPending(trade(0L, 100L));

        tradeService.accept(TRADE_ID, RECIPIENT);

        verify(essenceLedgerService).debit(RECIPIENT, 100L, EssenceReason.trade_payment, TRADE_ID);
        verify(essenceLedgerService).credit(SENDER, 100L, EssenceReason.trade_receipt, TRADE_ID);
    }

    @Test
    void decliningReleasesTheSendersHold() {
        stubPending(trade(100L, 0L));

        tradeService.decline(TRADE_ID, RECIPIENT);

        verify(essenceLedgerService).release(SENDER, 100L);
    }

    @Test
    void cancellingReleasesTheSendersHold() {
        stubPending(trade(100L, 0L));

        tradeService.cancel(TRADE_ID, SENDER);

        verify(essenceLedgerService).release(SENDER, 100L);
    }

    @Test
    void decliningATradeWithNoOfferedEssenceReleasesNothing() {
        stubPending(trade(0L, 100L));

        tradeService.decline(TRADE_ID, RECIPIENT);

        verify(essenceLedgerService, never()).release(anyLong(), anyLong());
    }

    @Test
    void expiryRefundsEveryStrandedHold() {
        when(tradeRepository.findExpiringWithReservedEssence(any())).thenReturn(List.of(trade(100L, 0L)));

        tradeService.expireOlderThan(java.time.Instant.now());

        verify(essenceLedgerService).release(SENDER, 100L);
        verify(tradeRepository).expirePending(any(), any());
    }

    private void stubPending(UserItemTrade trade) {
        when(tradeRepository.findById(TRADE_ID)).thenReturn(Optional.of(trade));
    }

    private static UserItemTrade trade(long offeredEssence, long requestedEssence) {
        return UserItemTrade.builder()
                .id(TRADE_ID)
                .fromUser(user(SENDER))
                .toUser(user(RECIPIENT))
                .status(TradeStatus.pending)
                .offeredEssence(offeredEssence)
                .requestedEssence(requestedEssence)
                .items(new ArrayList<>())
                .build();
    }

    private static User user(Long id) {
        return User.builder().id(id).name("player" + id).build();
    }
}
