package com.accsaber.backend.service.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
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
import org.springframework.test.util.ReflectionTestUtils;

import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemModifier;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.item.ItemModifierRepository;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.item.UserItemLinkRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.notification.NotificationService;
import com.accsaber.backend.service.player.DuplicateUserService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ItemServiceQuantityAwardTest {

    private static final Long USER_ID = 76561198087536397L;
    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final String NODE_ID = "b6f2c0de-0000-4000-8000-000000000001";

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private UserItemLinkRepository userItemLinkRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DuplicateUserService duplicateUserService;
    @Mock
    private ItemModifierRepository itemModifierRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ModifierResolver modifierResolver;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private ItemService itemService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(itemService, "entityManager", entityManager);
        when(duplicateUserService.resolvePrimaryUserId(USER_ID)).thenReturn(USER_ID);
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(User.builder().id(USER_ID).build());
        when(itemRepository.findByIdAndActiveTrue(ITEM_ID)).thenReturn(Optional.of(crate()));
        when(itemModifierRepository.findByKey(ItemModifier.NORMAL))
                .thenReturn(Optional.of(ItemModifier.builder().key(ItemModifier.NORMAL).name("Normal").build()));
        when(userItemLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void awardsOneRowPerCrateRatherThanOneStackOfTwo() {
        itemService.awardSystem(USER_ID, ITEM_ID, ItemSource.campaign_difficulty, NODE_ID, "reward", 2);

        ArgumentCaptor<UserItemLink> captor = ArgumentCaptor.forClass(UserItemLink.class);
        verify(userItemLinkRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .hasSize(2)
                .allSatisfy(link -> assertThat(link.getQuantity()).isEqualTo(1L));
    }

    @Test
    void notifiesOnceForTheWholeGrant() {
        itemService.awardSystem(USER_ID, ITEM_ID, ItemSource.campaign_difficulty, NODE_ID, "reward", 2);

        verify(notificationService, times(1)).notify(any(), any(), any(), any(), any());
    }

    @Test
    void grantsOnlyTheShortfallWhenTheSameSourceAlreadyPaidOut() {
        when(userItemLinkRepository.countByUser_IdAndItem_IdAndSourceAndSourceId(
                USER_ID, ITEM_ID, ItemSource.campaign_difficulty, NODE_ID)).thenReturn(1L);

        itemService.awardSystem(USER_ID, ITEM_ID, ItemSource.campaign_difficulty, NODE_ID, "reward", 2);

        verify(userItemLinkRepository, times(1)).save(any());
    }

    @Test
    void grantsNothingWhenTheSourceAlreadyPaidTheFullQuantity() {
        when(userItemLinkRepository.countByUser_IdAndItem_IdAndSourceAndSourceId(
                USER_ID, ITEM_ID, ItemSource.campaign_difficulty, NODE_ID)).thenReturn(2L);

        itemService.awardSystem(USER_ID, ITEM_ID, ItemSource.campaign_difficulty, NODE_ID, "reward", 2);

        verify(userItemLinkRepository, never()).save(any());
    }

    @Test
    void issuesADistinctSerialPerCopyOfASerializedItem() {
        Item serialized = crate();
        serialized.setSerialized(true);
        when(itemRepository.findByIdAndActiveTrue(ITEM_ID)).thenReturn(Optional.of(serialized));
        Query serialQuery = org.mockito.Mockito.mock(Query.class);
        when(entityManager.createNativeQuery(any(String.class))).thenReturn(serialQuery);
        when(serialQuery.setParameter(any(String.class), any())).thenReturn(serialQuery);
        when(serialQuery.getSingleResult()).thenReturn(4L, 5L);

        itemService.awardSystem(USER_ID, ITEM_ID, ItemSource.campaign_completion, NODE_ID, "reward", 2);

        ArgumentCaptor<UserItemLink> captor = ArgumentCaptor.forClass(UserItemLink.class);
        verify(userItemLinkRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(UserItemLink::getSerialNumber)
                .containsExactly(4L, 5L);
    }

    @Test
    void ignoresANonPositiveQuantity() {
        itemService.awardSystem(USER_ID, ITEM_ID, ItemSource.campaign_difficulty, NODE_ID, "reward", 0);

        verify(userItemLinkRepository, never()).save(any());
    }

    private static Item crate() {
        return Item.builder()
                .id(ITEM_ID)
                .type(ItemType.builder().key("crate").name("Crate").build())
                .name("Alpha Crate")
                .stackable(false)
                .uniquePerUser(false)
                .serialized(false)
                .active(true)
                .deprecated(false)
                .build();
    }
}
