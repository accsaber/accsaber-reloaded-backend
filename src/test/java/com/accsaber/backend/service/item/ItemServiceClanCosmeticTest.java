package com.accsaber.backend.service.item;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.item.UserItemLinkRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;

@ExtendWith(MockitoExtension.class)
class ItemServiceClanCosmeticTest {

    private static final Long USER_ID = 76561198087536397L;
    private static final UUID ITEM_ID = UUID.randomUUID();

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private UserItemLinkRepository userItemLinkRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DuplicateUserService duplicateUserService;

    @InjectMocks
    private ItemService itemService;

    @Test
    void aClanCosmeticNeverLandsInAPlayerInventory() {
        ItemType tagCard = ItemType.builder().key("clan_tag_card")
                .parentType(ItemType.builder().key("clan_cosmetic").build()).build();
        when(duplicateUserService.resolvePrimaryUserId(USER_ID)).thenReturn(USER_ID);
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(itemRepository.findByIdAndActiveTrue(ITEM_ID)).thenReturn(Optional.of(
                Item.builder().id(ITEM_ID).type(tagCard).name("Aurora Card").active(true).build()));

        assertThatThrownBy(() -> itemService.awardManual(USER_ID, ITEM_ID, null, "wrong shelf", null, 1L, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("clan cosmetic");

        verify(userItemLinkRepository, never()).save(any());
    }
}
