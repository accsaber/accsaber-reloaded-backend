package com.accsaber.backend.service.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserSettingKey;
import com.accsaber.backend.repository.item.UserItemLinkRepository;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.player.UserSettingsService;

@ExtendWith(MockitoExtension.class)
class ItemServiceEquippedLinksTest {

    private static final Long SECONDARY_ID = 111L;
    private static final Long PRIMARY_ID = 222L;

    @Mock
    private UserItemLinkRepository userItemLinkRepository;
    @Mock
    private DuplicateUserService duplicateUserService;
    @Mock
    private UserSettingsService userSettingsService;

    @InjectMocks
    private ItemEquipService itemEquipService;

    @Test
    void resolvesPrimaryUserAndIncludesFallbackPicks() {
        UserItemLink explicitTitle = link(PRIMARY_ID, "title");
        UserItemLink fallbackBorder = link(PRIMARY_ID, "profile_border_color");

        when(duplicateUserService.resolvePrimaryUserId(SECONDARY_ID)).thenReturn(PRIMARY_ID);
        when(userSettingsService.getGroup(PRIMARY_ID, UserSettingKey.GROUP_EQUIPPED))
                .thenReturn(Map.of("equipped.title", explicitTitle.getId().toString()));
        when(userItemLinkRepository.findAllById(List.of(explicitTitle.getId())))
                .thenReturn(List.of(explicitTitle));
        when(userItemLinkRepository.findOwnedByTypeKeys(eq(PRIMARY_ID), anySet()))
                .thenReturn(List.of(fallbackBorder));

        List<UserItemLink> links = itemEquipService.findEffectiveEquippedLinks(SECONDARY_ID);

        assertThat(links).containsExactlyInAnyOrder(explicitTitle, fallbackBorder);
    }

    @Test
    void explicitLinkOwnedByAnotherUserIsDropped() {
        UserItemLink foreign = link(999L, "title");

        when(duplicateUserService.resolvePrimaryUserId(PRIMARY_ID)).thenReturn(PRIMARY_ID);
        when(userSettingsService.getGroup(PRIMARY_ID, UserSettingKey.GROUP_EQUIPPED))
                .thenReturn(Map.of("equipped.title", foreign.getId().toString()));
        when(userItemLinkRepository.findAllById(List.of(foreign.getId())))
                .thenReturn(List.of(foreign));
        when(userItemLinkRepository.findOwnedByTypeKeys(eq(PRIMARY_ID), anySet()))
                .thenReturn(List.of());

        List<UserItemLink> links = itemEquipService.findEffectiveEquippedLinks(PRIMARY_ID);

        assertThat(links).isEmpty();
    }

    private static UserItemLink link(Long ownerId, String typeKey) {
        return UserItemLink.builder()
                .id(UUID.randomUUID())
                .user(User.builder().id(ownerId).build())
                .item(Item.builder()
                        .id(UUID.randomUUID())
                        .type(ItemType.builder().id(UUID.randomUUID()).key(typeKey).build())
                        .build())
                .build();
    }
}
