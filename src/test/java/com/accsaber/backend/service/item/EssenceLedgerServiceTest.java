package com.accsaber.backend.service.item;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.item.EssenceReason;
import com.accsaber.backend.repository.item.EssenceTransactionRepository;
import com.accsaber.backend.repository.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class EssenceLedgerServiceTest {

    private static final Long BUYER = 1L;
    private static final Long SELLER = 2L;
    private static final UUID REF = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;
    @Mock
    private EssenceTransactionRepository transactionRepository;

    @InjectMocks
    private EssenceLedgerService ledger;

    @Test
    void reserveRejectsWhenBalanceIsInsufficient() {
        when(userRepository.reserveEssence(BUYER, 100L)).thenReturn(0);

        assertThatThrownBy(() -> ledger.reserve(BUYER, 100L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("insufficient essence");
    }

    @Test
    void reserveDoesNotWriteALedgerRowBecauseHoldsAreNotEconomicEvents() {
        when(userRepository.reserveEssence(BUYER, 100L)).thenReturn(1);

        ledger.reserve(BUYER, 100L);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void debitRejectsWhenBalanceIsInsufficient() {
        when(userRepository.debitEssence(BUYER, 100L)).thenReturn(0);

        assertThatThrownBy(() -> ledger.debit(BUYER, 100L, EssenceReason.trade_payment, REF))
                .isInstanceOf(ValidationException.class);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void settleReservedMovesHeldEssenceToTheSeller() {
        when(userRepository.consumeReservedEssence(BUYER, 250L)).thenReturn(1);

        ledger.settleReserved(BUYER, SELLER, 250L, EssenceReason.purchase, EssenceReason.sale, REF);

        verify(userRepository).consumeReservedEssence(BUYER, 250L);
        verify(userRepository).addItemEssence(SELLER, 250L);
        verify(transactionRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void settleReservedFailsWhenTheHoldIsMissing() {
        when(userRepository.consumeReservedEssence(BUYER, 250L)).thenReturn(0);

        assertThatThrownBy(() -> ledger.settleReserved(BUYER, SELLER, 250L,
                EssenceReason.purchase, EssenceReason.sale, REF))
                .isInstanceOf(ConflictException.class);

        verify(userRepository, never()).addItemEssence(anyLong(), anyLong());
    }

    @Test
    void releaseFailsWhenReservedIsLowerThanTheAmount() {
        when(userRepository.releaseEssence(BUYER, 50L)).thenReturn(0);

        assertThatThrownBy(() -> ledger.release(BUYER, 50L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void nonPositiveAmountsAreRejected() {
        assertThatThrownBy(() -> ledger.reserve(BUYER, 0L)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> ledger.credit(BUYER, -5L, EssenceReason.disintegration, REF))
                .isInstanceOf(ValidationException.class);
    }
}
