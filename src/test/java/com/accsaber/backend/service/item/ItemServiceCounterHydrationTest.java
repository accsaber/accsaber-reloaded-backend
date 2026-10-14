package com.accsaber.backend.service.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.accsaber.backend.model.dto.response.item.UserItemResponse;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemModifier;
import com.accsaber.backend.model.entity.item.ItemRarity;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.repository.item.UserItemLinkCounterRepository;
import com.accsaber.backend.repository.item.UserItemLinkRepository;
import com.accsaber.backend.repository.item.UserItemTradeItemRepository;
import com.accsaber.backend.service.player.DuplicateUserService;

@ExtendWith(MockitoExtension.class)
class ItemServiceCounterHydrationTest {

    private static final Long USER_ID = 76561198087536397L;

    @Mock
    private UserItemLinkRepository userItemLinkRepository;
    @Mock
    private UserItemLinkCounterRepository counterRepository;
    @Mock
    private DuplicateUserService duplicateUserService;
    @Mock
    private UserItemTradeItemRepository tradeItemRepository;

    @InjectMocks
    private ItemService itemService;

    @Test
    void collectionCarriesStrangeCounters() {
        UserItemLink strange = link();
        when(duplicateUserService.resolvePrimaryUserId(USER_ID)).thenReturn(USER_ID);
        when(userItemLinkRepository.findByUser_IdAndEscrowedFalse(USER_ID)).thenReturn(List.of(strange));
        when(counterRepository.countersByLink(any()))
                .thenReturn(Map.of(strange.getId(), Map.of(StrangeTrackingService.STAT_PLAY_COUNT, 42L)));

        List<UserItemResponse> responses = itemService.findUserCollectionHydrated(USER_ID, null);

        assertThat(responses).singleElement()
                .extracting(UserItemResponse::getCounters)
                .isEqualTo(Map.of(StrangeTrackingService.STAT_PLAY_COUNT, 42L));
    }

    @Test
    void inventoryCarriesStrangeCounters() {
        UserItemLink strange = link();
        Page<Object[]> page = new PageImpl<>(List.<Object[]>of(new Object[] { strange.getItem(), strange }),
                PageRequest.of(0, 20), 1);
        when(duplicateUserService.resolvePrimaryUserId(USER_ID)).thenReturn(USER_ID);
        when(userItemLinkRepository.findInventoryFiltered(any(), anyBoolean(), any(), any(), any(), any(), any(),
                any(), any(), any(), any())).thenReturn(page);
        when(counterRepository.countersByLink(any()))
                .thenReturn(Map.of(strange.getId(), Map.of(StrangeTrackingService.STAT_PLAY_COUNT, 7L)));

        Page<UserItemResponse> responses = itemService.findInventoryHydrated(USER_ID, null, PageRequest.of(0, 20));

        assertThat(responses.getContent()).singleElement()
                .extracting(UserItemResponse::getCounters)
                .isEqualTo(Map.of(StrangeTrackingService.STAT_PLAY_COUNT, 7L));
    }

    @Test
    void linksWithoutCountersStayNull() {
        UserItemLink plain = link();
        when(duplicateUserService.resolvePrimaryUserId(USER_ID)).thenReturn(USER_ID);
        when(userItemLinkRepository.findByUser_IdAndEscrowedFalse(USER_ID)).thenReturn(List.of(plain));
        when(counterRepository.countersByLink(any())).thenReturn(Map.of());

        List<UserItemResponse> responses = itemService.findUserCollectionHydrated(USER_ID, null);

        assertThat(responses).singleElement()
                .extracting(UserItemResponse::getCounters)
                .isNull();
    }

    private static UserItemLink link() {
        Item item = Item.builder()
                .id(UUID.randomUUID())
                .type(ItemType.builder().id(UUID.randomUUID()).key("profile_border_color").build())
                .name("Aquamarine")
                .rarity(ItemRarity.common)
                .build();
        return UserItemLink.builder()
                .id(UUID.randomUUID())
                .item(item)
                .modifiers(Set.of(ItemModifier.builder()
                        .id(UUID.randomUUID())
                        .key(ItemModifier.STRANGE)
                        .build()))
                .source(ItemSource.manual)
                .build();
    }
}
