package com.accsaber.backend.service.item;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.entity.item.ItemModifier;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.repository.item.UserItemLinkCounterRepository;

@ExtendWith(MockitoExtension.class)
class StrangeTrackingServiceTest {

    private static final Long USER_ID = 76561198087536397L;

    @Mock
    private ItemEquipService itemEquipService;
    @Mock
    private UserItemLinkCounterRepository counterRepository;

    @InjectMocks
    private StrangeTrackingService strangeTrackingService;

    @Test
    void everyEquippedStrangeLinkIsIncremented() {
        UserItemLink strangeBorder = link("strange", "founders");
        UserItemLink strangeTitle = link("strange");
        UserItemLink normal = link("normal");
        when(itemEquipService.findEffectiveEquippedLinks(USER_ID))
                .thenReturn(List.of(strangeBorder, strangeTitle, normal));

        strangeTrackingService.recordPlay(USER_ID);

        verify(counterRepository).incrementBy(strangeBorder.getId(), StrangeTrackingService.STAT_PLAY_COUNT, 1L);
        verify(counterRepository).incrementBy(strangeTitle.getId(), StrangeTrackingService.STAT_PLAY_COUNT, 1L);
        verify(counterRepository, never()).incrementBy(eq(normal.getId()), eq(StrangeTrackingService.STAT_PLAY_COUNT),
                eq(1L));
    }

    @Test
    void nothingEquippedIncrementsNothing() {
        when(itemEquipService.findEffectiveEquippedLinks(USER_ID)).thenReturn(List.of());

        strangeTrackingService.recordPlay(USER_ID);

        verifyNoInteractions(counterRepository);
    }

    @Test
    void aFailingLookupNeverPropagates() {
        when(itemEquipService.findEffectiveEquippedLinks(USER_ID)).thenThrow(new IllegalStateException("boom"));

        strangeTrackingService.recordPlay(USER_ID);

        verifyNoInteractions(counterRepository);
    }

    private static UserItemLink link(String... modifierKeys) {
        Set<ItemModifier> modifiers = new java.util.HashSet<>();
        for (String key : modifierKeys) {
            modifiers.add(ItemModifier.builder().id(UUID.randomUUID()).key(key).build());
        }
        return UserItemLink.builder()
                .id(UUID.randomUUID())
                .modifiers(modifiers)
                .build();
    }
}
