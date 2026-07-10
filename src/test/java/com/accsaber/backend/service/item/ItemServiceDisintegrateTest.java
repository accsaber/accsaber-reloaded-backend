package com.accsaber.backend.service.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.item.DisintegrationResponse;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.model.entity.item.TradeStatus;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserSettingKey;
import com.accsaber.backend.repository.item.UserItemDisintegrationRepository;
import com.accsaber.backend.repository.item.UserItemLinkRepository;
import com.accsaber.backend.repository.item.UserItemTradeItemRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.player.UserSettingsService;

@ExtendWith(MockitoExtension.class)
class ItemServiceDisintegrateTest {

    private static final Long USER_ID = 7L;
    private static final UUID LINK_ID = UUID.randomUUID();

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
    private UserSettingsService userSettingsService;

    @InjectMocks
    private ItemService itemService;

    @Test
    void disintegrateWholeLinkAddsEssenceAndDeletesLink() {
        UserItemLink link = link(item("material", new BigDecimal("50"), false), 1L);
        stubOwnedLink(link);
        stubNotInTrade();
        when(userRepository.findItemEssenceById(USER_ID)).thenReturn(Optional.of(new BigDecimal("50")));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(link.getUser());

        DisintegrationResponse res = itemService.disintegrate(USER_ID, LINK_ID, null);

        verify(userItemLinkRepository).delete(link);
        verify(userRepository).addItemEssence(eq(USER_ID), eq(new BigDecimal("50")));
        verify(disintegrationRepository).save(any());
        assertThat(res.getEssenceGained()).isEqualByComparingTo("50");
        assertThat(res.getBalance()).isEqualByComparingTo("50");
        assertThat(res.getRemainingQuantity()).isNull();
        assertThat(res.getQuantityDisintegrated()).isEqualTo(1L);
    }

    @Test
    void disintegratePartialStackDecrementsQuantity() {
        UserItemLink link = link(item("material", new BigDecimal("5"), true), 10L);
        stubOwnedLink(link);
        stubNotInTrade();
        when(userRepository.findItemEssenceById(USER_ID)).thenReturn(Optional.of(new BigDecimal("15")));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(link.getUser());

        DisintegrationResponse res = itemService.disintegrate(USER_ID, LINK_ID, 3L);

        verify(userItemLinkRepository, never()).delete(any());
        verify(userItemLinkRepository).save(link);
        verify(userRepository).addItemEssence(eq(USER_ID), eq(new BigDecimal("15")));
        assertThat(link.getQuantity()).isEqualTo(7L);
        assertThat(res.getRemainingQuantity()).isEqualTo(7L);
        assertThat(res.getQuantityDisintegrated()).isEqualTo(3L);
        assertThat(res.getEssenceGained()).isEqualByComparingTo("15");
    }

    @Test
    void disintegrateRejectsItemWithoutWorth() {
        stubOwnedLink(link(item("material", null, false), 1L));

        assertThatThrownBy(() -> itemService.disintegrate(USER_ID, LINK_ID, null))
                .isInstanceOf(ValidationException.class);

        verify(userRepository, never()).addItemEssence(any(), any());
    }

    @Test
    void disintegrateBlocksItemInPendingTrade() {
        stubOwnedLink(link(item("material", new BigDecimal("50"), false), 1L));
        when(tradeItemRepository.findLinkIdsInTradesWithStatus(List.of(LINK_ID), TradeStatus.pending))
                .thenReturn(List.of(LINK_ID));

        assertThatThrownBy(() -> itemService.disintegrate(USER_ID, LINK_ID, null))
                .isInstanceOf(ConflictException.class);

        verify(userRepository, never()).addItemEssence(any(), any());
    }

    @Test
    void disintegrateBlocksEquippedItem() {
        stubOwnedLink(link(item("title", new BigDecimal("50"), false), 1L));
        stubNotInTrade();
        when(userSettingsService.get(USER_ID, UserSettingKey.EQUIPPED_TITLE, UUID.class)).thenReturn(LINK_ID);

        assertThatThrownBy(() -> itemService.disintegrate(USER_ID, LINK_ID, null))
                .isInstanceOf(ConflictException.class);

        verify(userRepository, never()).addItemEssence(any(), any());
    }

    @Test
    void disintegrateRejectsUntradeableItem() {
        stubOwnedLink(link(item("material", new BigDecimal("50"), false, false), 1L));

        assertThatThrownBy(() -> itemService.disintegrate(USER_ID, LINK_ID, null))
                .isInstanceOf(ValidationException.class);

        verify(userRepository, never()).addItemEssence(any(), any());
    }

    @Test
    void disintegrateRejectsQuantityAboveOwned() {
        stubOwnedLink(link(item("material", new BigDecimal("5"), true), 2L));
        stubNotInTrade();

        assertThatThrownBy(() -> itemService.disintegrate(USER_ID, LINK_ID, 3L))
                .isInstanceOf(ValidationException.class);

        verify(userRepository, never()).addItemEssence(any(), any());
    }

    private void stubOwnedLink(UserItemLink link) {
        when(duplicateUserService.resolvePrimaryUserId(USER_ID)).thenReturn(USER_ID);
        when(userItemLinkRepository.findByIdForUpdate(LINK_ID)).thenReturn(Optional.of(link));
    }

    private void stubNotInTrade() {
        when(tradeItemRepository.findLinkIdsInTradesWithStatus(List.of(LINK_ID), TradeStatus.pending))
                .thenReturn(List.of());
    }

    private static User user() {
        return User.builder().id(USER_ID).name("player").build();
    }

    private static Item item(String typeKey, BigDecimal worth, boolean stackable) {
        return item(typeKey, worth, stackable, true);
    }

    private static Item item(String typeKey, BigDecimal worth, boolean stackable, boolean tradeable) {
        return Item.builder()
                .id(UUID.randomUUID())
                .type(ItemType.builder().key(typeKey).name(typeKey).build())
                .name("thing")
                .worth(worth)
                .stackable(stackable)
                .tradeable(tradeable)
                .build();
    }

    private static UserItemLink link(Item item, long quantity) {
        return UserItemLink.builder()
                .id(LINK_ID)
                .user(user())
                .item(item)
                .quantity(quantity)
                .build();
    }
}
