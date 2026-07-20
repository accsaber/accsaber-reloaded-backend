package com.accsaber.backend.service.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.item.UserItemLinkRepository;
import com.accsaber.backend.repository.user.UserRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ItemTransferServiceTest {

    private static final Long SELLER_ID = 1L;
    private static final Long BUYER_ID = 2L;

    @Mock
    private UserItemLinkRepository userItemLinkRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ItemService itemService;

    @InjectMocks
    private ItemTransferService transferService;

    @BeforeEach
    void setUp() {
        when(userItemLinkRepository.save(any())).thenAnswer(returnsFirstArg());
        when(userRepository.getReferenceById(BUYER_ID)).thenReturn(user(BUYER_ID));
    }

    @Test
    void escrowingPartOfAStackSplitsIt() {
        UserItemLink source = link(SELLER_ID, stackable(), 10L);

        UserItemLink escrowed = transferService.escrow(source, 3L);

        assertThat(source.getQuantity()).isEqualTo(7L);
        assertThat(source.isEscrowed()).isFalse();
        assertThat(escrowed.getQuantity()).isEqualTo(3L);
        assertThat(escrowed.isEscrowed()).isTrue();
        assertThat(escrowed.getUser().getId()).isEqualTo(SELLER_ID);
    }

    @Test
    void escrowingAWholeStackFlagsTheLinkInPlace() {
        UserItemLink source = link(SELLER_ID, stackable(), 10L);

        UserItemLink escrowed = transferService.escrow(source, 10L);

        assertThat(escrowed).isSameAs(source);
        assertThat(escrowed.isEscrowed()).isTrue();
        verify(userItemLinkRepository, never()).delete(any());
    }

    @Test
    void aUniqueInstanceCannotBeSplit() {
        UserItemLink source = link(SELLER_ID, nonStackable(), 3L);

        assertThatThrownBy(() -> transferService.escrow(source, 1L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot be split");
    }

    @Test
    void aSerialNumberedStackIsTreatedAsAUniqueInstance() {
        UserItemLink source = link(SELLER_ID, stackable(), 3L);
        source.setSerialNumber(1L);

        assertThatThrownBy(() -> transferService.escrow(source, 1L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot be split");
    }

    @Test
    void escrowRejectsMoreThanIsOwned() {
        UserItemLink source = link(SELLER_ID, stackable(), 2L);

        assertThatThrownBy(() -> transferService.escrow(source, 5L))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void awardingAnEscrowedStackMergesIntoTheBuyersExistingStackWithoutDeletingTheEscrowRow() {
        Item item = stackable();
        UserItemLink escrowLink = link(SELLER_ID, item, 3L);
        escrowLink.setEscrowed(true);
        UserItemLink buyerStack = link(BUYER_ID, item, 4L);
        when(userItemLinkRepository.findByUser_IdAndItem_IdAndEscrowedFalse(BUYER_ID, item.getId()))
                .thenReturn(List.of(buyerStack));

        transferService.transferEscrowed(escrowLink, BUYER_ID, ItemSource.market, "Purchased on the market");

        assertThat(escrowLink.getQuantity()).isEqualTo(7L);
        assertThat(escrowLink.isEscrowed()).isFalse();
        assertThat(escrowLink.getUser().getId()).isEqualTo(BUYER_ID);
        assertThat(escrowLink.getSource()).isEqualTo(ItemSource.market);

        ArgumentCaptor<UserItemLink> deleted = ArgumentCaptor.forClass(UserItemLink.class);
        verify(userItemLinkRepository).delete(deleted.capture());
        assertThat(deleted.getValue()).isSameAs(buyerStack);
    }

    @Test
    void awardingClearsTheSellersEquippedSlot() {
        Item item = nonStackable();
        UserItemLink escrowLink = link(SELLER_ID, item, 1L);
        escrowLink.setEscrowed(true);

        transferService.transferEscrowed(escrowLink, BUYER_ID, ItemSource.market, "Purchased on the market");

        verify(itemService).clearEquippedIfLinkGone(SELLER_ID, escrowLink.getId(), "title");
    }

    @Test
    void releasingEscrowMergesBackIntoTheSellersRemainingStack() {
        Item item = stackable();
        UserItemLink escrowLink = link(SELLER_ID, item, 3L);
        escrowLink.setEscrowed(true);
        UserItemLink remainder = link(SELLER_ID, item, 7L);
        when(userItemLinkRepository.findByUser_IdAndItem_IdAndEscrowedFalse(SELLER_ID, item.getId()))
                .thenReturn(List.of(remainder));

        transferService.releaseEscrow(escrowLink);

        assertThat(escrowLink.getQuantity()).isEqualTo(10L);
        assertThat(escrowLink.isEscrowed()).isFalse();
        verify(userItemLinkRepository).delete(remainder);
    }

    private static Item stackable() {
        return item(true);
    }

    private static Item nonStackable() {
        return item(false);
    }

    private static Item item(boolean stackable) {
        return Item.builder()
                .id(UUID.randomUUID())
                .type(ItemType.builder().key("title").name("Title").build())
                .name("A thing")
                .stackable(stackable)
                .tradeable(true)
                .build();
    }

    private static UserItemLink link(Long ownerId, Item item, long quantity) {
        return UserItemLink.builder()
                .id(UUID.randomUUID())
                .user(user(ownerId))
                .item(item)
                .modifiers(Set.of())
                .quantity(quantity)
                .source(ItemSource.manual)
                .build();
    }

    private static User user(Long id) {
        return User.builder().id(id).name("player" + id).build();
    }
}
