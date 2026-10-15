package com.accsaber.backend.service.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemModifier;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.item.ItemModifierRepository;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.item.UserItemLinkRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.notification.NotificationService;
import com.accsaber.backend.service.player.DuplicateUserService;

@ExtendWith(MockitoExtension.class)
class ItemServiceStackAttributionTest {

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

    @InjectMocks
    private ItemAwardService itemAwardService;

    @Test
    void aMissionGrantMergedIntoAStaffAwardedStackNoLongerClaimsToBeStaffAwarded() {
        ItemModifier normal = modifier();
        UserItemLink staffAwarded = UserItemLink.builder()
                .id(UUID.randomUUID())
                .user(User.builder().id(USER_ID).build())
                .item(crate())
                .modifiers(Set.of(normal))
                .quantity(2L)
                .source(ItemSource.manual)
                .awardedBy(StaffUser.builder().id(UUID.randomUUID()).build())
                .reason("Compensation")
                .build();
        stubStack(normal, staffAwarded);

        String missionId = UUID.randomUUID().toString();
        itemAwardService.awardSystem(USER_ID, ITEM_ID, ItemSource.mission, missionId, "Mission reward: Daily grind");

        assertThat(staffAwarded.getQuantity()).isEqualTo(3L);
        assertThat(staffAwarded.getSource()).isEqualTo(ItemSource.mission);
        assertThat(staffAwarded.getSourceId()).isEqualTo(missionId);
        assertThat(staffAwarded.getAwardedBy()).isNull();
        assertThat(staffAwarded.getReason()).isEqualTo("Mission reward: Daily grind");
    }

    private void stubStack(ItemModifier normal, UserItemLink existing) {
        when(duplicateUserService.resolvePrimaryUserId(USER_ID)).thenReturn(USER_ID);
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(itemRepository.findByIdAndActiveTrue(ITEM_ID)).thenReturn(Optional.of(crate()));
        when(itemModifierRepository.findByKey(ItemModifier.NORMAL)).thenReturn(Optional.of(normal));
        when(userItemLinkRepository.findStacksForUpdate(USER_ID, ITEM_ID))
                .thenReturn(List.of(existing));
        when(userItemLinkRepository.save(any(UserItemLink.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Item crate() {
        return Item.builder()
                .id(ITEM_ID)
                .type(ItemType.builder().key("crate").name("Crate").build())
                .name("Alpha Crate")
                .stackable(true)
                .active(true)
                .deprecated(false)
                .build();
    }

    private static ItemModifier modifier() {
        ItemModifier m = new ItemModifier();
        m.setId(UUID.randomUUID());
        m.setKey(ItemModifier.NORMAL);
        return m;
    }
}
