package com.accsaber.backend.service.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.item.DisintegrateRequest;
import com.accsaber.backend.model.dto.response.item.DisintegrationResponse;
import com.accsaber.backend.model.entity.item.EssenceReason;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.model.entity.item.TradeStatus;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.item.UserItemDisintegrationRepository;
import com.accsaber.backend.repository.item.UserItemLinkRepository;
import com.accsaber.backend.repository.item.UserItemTradeItemRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;

@ExtendWith(MockitoExtension.class)
class ItemDisintegrationServiceTest {

    private static final Long USER_ID = 7L;
    private static final UUID LINK_ID = UUID.randomUUID();
    private static final UUID OTHER_LINK_ID = UUID.randomUUID();

    @Mock
    private DuplicateUserService duplicateUserService;
    @Mock
    private UserItemLinkRepository userItemLinkRepository;
    @Mock
    private UserItemTradeItemRepository tradeItemRepository;
    @Mock
    private UserItemDisintegrationRepository disintegrationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ItemEquipService itemEquipService;
    @Mock
    private EssenceLedgerService essenceLedgerService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ItemDisintegrationService itemDisintegrationService;

    @Test
    void disintegrateWholeLinkAddsEssenceAndDeletesLink() {
        UserItemLink link = link(LINK_ID, item("material", 50L, false), 1L);
        stubOwnedLinks(link);
        stubNotInTrade(LINK_ID);
        stubNothingEquipped();
        when(essenceLedgerService.balance(USER_ID)).thenReturn(50L);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(link.getUser());

        DisintegrationResponse res = itemDisintegrationService.disintegrate(USER_ID, List.of(entry(LINK_ID, null)));

        verify(userItemLinkRepository).deleteAllByIdInBatch(Set.of(LINK_ID));
        verify(essenceLedgerService).creditAll(USER_ID, EssenceReason.disintegration, Map.of(LINK_ID, 50L));
        verify(disintegrationRepository).saveAll(any());
        assertThat(res.getEssenceGained()).isEqualTo(50L);
        assertThat(res.getBalance()).isEqualTo(50L);
        assertThat(res.getEntries()).singleElement().satisfies(entry -> {
            assertThat(entry.getRemainingQuantity()).isNull();
            assertThat(entry.getQuantityDisintegrated()).isEqualTo(1L);
            assertThat(entry.getEssenceGained()).isEqualTo(50L);
        });
    }

    @Test
    void disintegratePartialStackDecrementsQuantity() {
        UserItemLink link = link(LINK_ID, item("material", 5L, true), 10L);
        stubOwnedLinks(link);
        stubNotInTrade(LINK_ID);
        stubNothingEquipped();
        when(essenceLedgerService.balance(USER_ID)).thenReturn(15L);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(link.getUser());

        DisintegrationResponse res = itemDisintegrationService.disintegrate(USER_ID, List.of(entry(LINK_ID, 3L)));

        verify(userItemLinkRepository, never()).deleteAllByIdInBatch(any());
        verify(userItemLinkRepository).saveAll(List.of(link));
        verify(essenceLedgerService).creditAll(USER_ID, EssenceReason.disintegration, Map.of(LINK_ID, 15L));
        assertThat(link.getQuantity()).isEqualTo(7L);
        assertThat(res.getEntries()).singleElement().satisfies(entry -> {
            assertThat(entry.getRemainingQuantity()).isEqualTo(7L);
            assertThat(entry.getQuantityDisintegrated()).isEqualTo(3L);
        });
    }

    @Test
    void disintegrateManyItemsCreditsEssenceOnce() {
        UserItemLink whole = link(LINK_ID, item("material", 50L, false), 1L);
        UserItemLink stack = link(OTHER_LINK_ID, item("material", 5L, true), 10L);
        stubOwnedLinks(whole, stack);
        stubNotInTrade(LINK_ID, OTHER_LINK_ID);
        stubNothingEquipped();
        when(essenceLedgerService.balance(USER_ID)).thenReturn(70L);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(whole.getUser());

        DisintegrationResponse res = itemDisintegrationService.disintegrate(USER_ID,
                List.of(entry(LINK_ID, null), entry(OTHER_LINK_ID, 4L)));

        verify(userItemLinkRepository).deleteAllByIdInBatch(Set.of(LINK_ID));
        verify(userItemLinkRepository).saveAll(List.of(stack));
        verify(essenceLedgerService).creditAll(USER_ID, EssenceReason.disintegration,
                Map.of(LINK_ID, 50L, OTHER_LINK_ID, 20L));
        assertThat(stack.getQuantity()).isEqualTo(6L);
        assertThat(res.getEssenceGained()).isEqualTo(70L);
        assertThat(res.getEntries()).hasSize(2);
    }

    @Test
    void disintegrateRejectsTheSameItemTwice() {
        assertThatThrownBy(() -> itemDisintegrationService.disintegrate(USER_ID,
                List.of(entry(LINK_ID, 1L), entry(LINK_ID, 2L))))
                .isInstanceOf(ValidationException.class);

        verifyNoEssenceCredited();
    }

    @Test
    void disintegrateRejectsUnknownLink() {
        when(duplicateUserService.resolvePrimaryUserId(USER_ID)).thenReturn(USER_ID);
        when(userItemLinkRepository.findAllByIdForUpdate(Set.of(LINK_ID))).thenReturn(List.of());

        assertThatThrownBy(() -> itemDisintegrationService.disintegrate(USER_ID, List.of(entry(LINK_ID, null))))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoEssenceCredited();
    }

    @Test
    void disintegrateRejectsItemWithoutWorth() {
        stubOwnedLinks(link(LINK_ID, item("material", null, false), 1L));
        stubNotInTrade(LINK_ID);
        stubNothingEquipped();

        assertThatThrownBy(() -> itemDisintegrationService.disintegrate(USER_ID, List.of(entry(LINK_ID, null))))
                .isInstanceOf(ValidationException.class);

        verifyNoEssenceCredited();
    }

    @Test
    void disintegrateBlocksItemInPendingTrade() {
        stubOwnedLinks(link(LINK_ID, item("material", 50L, false), 1L));
        when(tradeItemRepository.findLinkIdsInTradesWithStatus(Set.of(LINK_ID), TradeStatus.pending))
                .thenReturn(List.of(LINK_ID));

        assertThatThrownBy(() -> itemDisintegrationService.disintegrate(USER_ID, List.of(entry(LINK_ID, null))))
                .isInstanceOf(ConflictException.class);

        verifyNoEssenceCredited();
    }

    @Test
    void disintegrateBlocksEscrowedItem() {
        UserItemLink link = link(LINK_ID, item("material", 50L, false), 1L);
        link.setEscrowed(true);
        stubOwnedLinks(link);
        stubNotInTrade(LINK_ID);
        stubNothingEquipped();

        assertThatThrownBy(() -> itemDisintegrationService.disintegrate(USER_ID, List.of(entry(LINK_ID, null))))
                .isInstanceOf(ConflictException.class);

        verifyNoEssenceCredited();
    }

    @Test
    void disintegrateBlocksEquippedItem() {
        stubOwnedLinks(link(LINK_ID, item("title", 50L, false), 1L));
        stubNotInTrade(LINK_ID);
        when(itemEquipService.equippedLinkIds(USER_ID)).thenReturn(Set.of(LINK_ID));

        assertThatThrownBy(() -> itemDisintegrationService.disintegrate(USER_ID, List.of(entry(LINK_ID, null))))
                .isInstanceOf(ConflictException.class);

        verifyNoEssenceCredited();
    }

    @Test
    void disintegrateRejectsUntradeableItem() {
        stubOwnedLinks(link(LINK_ID, item("material", 50L, false, false), 1L));
        stubNotInTrade(LINK_ID);
        stubNothingEquipped();

        assertThatThrownBy(() -> itemDisintegrationService.disintegrate(USER_ID, List.of(entry(LINK_ID, null))))
                .isInstanceOf(ValidationException.class);

        verifyNoEssenceCredited();
    }

    @Test
    void disintegrateRejectsQuantityAboveOwned() {
        stubOwnedLinks(link(LINK_ID, item("material", 5L, true), 2L));
        stubNotInTrade(LINK_ID);
        stubNothingEquipped();

        assertThatThrownBy(() -> itemDisintegrationService.disintegrate(USER_ID, List.of(entry(LINK_ID, 3L))))
                .isInstanceOf(ValidationException.class);

        verifyNoEssenceCredited();
    }

    private void verifyNoEssenceCredited() {
        verify(essenceLedgerService, never()).creditAll(any(), any(), any());
    }

    private void stubOwnedLinks(UserItemLink... links) {
        Set<UUID> ids = Arrays.stream(links).map(UserItemLink::getId).collect(Collectors.toSet());
        when(duplicateUserService.resolvePrimaryUserId(USER_ID)).thenReturn(USER_ID);
        when(userItemLinkRepository.findAllByIdForUpdate(ids)).thenReturn(List.of(links));
    }

    private void stubNotInTrade(UUID... linkIds) {
        when(tradeItemRepository.findLinkIdsInTradesWithStatus(Set.of(linkIds), TradeStatus.pending))
                .thenReturn(List.of());
    }

    private void stubNothingEquipped() {
        when(itemEquipService.equippedLinkIds(USER_ID)).thenReturn(Set.of());
    }

    private static DisintegrateRequest.Entry entry(UUID linkId, Long quantity) {
        DisintegrateRequest.Entry entry = new DisintegrateRequest.Entry();
        entry.setLinkId(linkId);
        entry.setQuantity(quantity);
        return entry;
    }

    private static User user() {
        return User.builder().id(USER_ID).name("player").build();
    }

    private static Item item(String typeKey, Long worth, boolean stackable) {
        return item(typeKey, worth, stackable, true);
    }

    private static Item item(String typeKey, Long worth, boolean stackable, boolean tradeable) {
        return Item.builder()
                .id(UUID.randomUUID())
                .type(ItemType.builder().key(typeKey).name(typeKey).build())
                .name("thing")
                .worth(worth)
                .stackable(stackable)
                .tradeable(tradeable)
                .build();
    }

    private static UserItemLink link(UUID id, Item item, long quantity) {
        return UserItemLink.builder()
                .id(id)
                .user(user())
                .item(item)
                .quantity(quantity)
                .build();
    }
}
