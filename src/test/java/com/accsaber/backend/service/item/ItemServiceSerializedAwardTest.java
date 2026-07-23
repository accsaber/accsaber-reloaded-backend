package com.accsaber.backend.service.item;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemModifier;
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

@ExtendWith(MockitoExtension.class)
class ItemServiceSerializedAwardTest {

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
    @Mock
    private ItemModifierRepository itemModifierRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private ItemService itemService;

    @Test
    void manualAwardOfNonSerializedItemHasNoSerialAndNoFounders() {
        Item saber = Item.builder()
                .id(ITEM_ID)
                .type(ItemType.builder().key("saber").name("Saber").build())
                .name("ACC God Saber")
                .stackable(false)
                .serialized(false)
                .uniquePerUser(true)
                .active(true)
                .deprecated(false)
                .build();
        when(duplicateUserService.resolvePrimaryUserId(USER_ID)).thenReturn(USER_ID);
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(itemRepository.findByIdAndActiveTrue(ITEM_ID)).thenReturn(Optional.of(saber));
        when(userItemLinkRepository.existsByUser_IdAndItem_Id(USER_ID, ITEM_ID)).thenReturn(false);
        when(itemModifierRepository.findByKey(ItemModifier.NORMAL))
                .thenReturn(Optional.of(modifier(ItemModifier.NORMAL)));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(new User());
        when(userItemLinkRepository.save(any(UserItemLink.class))).thenAnswer(inv -> inv.getArgument(0));

        UserItemLink link = itemService.awardManual(USER_ID, ITEM_ID, null, "test", null, 1L, null);

        assertThat(link.getSerialNumber()).isNull();
        assertThat(link.getModifiers()).extracting(ItemModifier::getKey)
                .containsExactly(ItemModifier.NORMAL)
                .doesNotContain(ItemModifier.FOUNDERS);
        verify(entityManager, never()).createNativeQuery(any(String.class));
    }

    private static ItemModifier modifier(String key) {
        ItemModifier m = new ItemModifier();
        m.setKey(key);
        return m;
    }
}
