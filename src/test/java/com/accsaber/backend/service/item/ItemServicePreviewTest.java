package com.accsaber.backend.service.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.item.UserItemResponse;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemModifier;
import com.accsaber.backend.model.entity.item.ItemRarity;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.model.entity.item.UnusualEffect;
import com.accsaber.backend.repository.item.ItemModifierRepository;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.item.UnusualEffectRepository;

@ExtendWith(MockitoExtension.class)
class ItemServicePreviewTest {

    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final UUID EFFECT_ID = UUID.randomUUID();

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private ItemModifierRepository itemModifierRepository;
    @Mock
    private UnusualEffectRepository unusualEffectRepository;

    @InjectMocks
    private ItemService itemService;

    @Test
    void previewReturnsFullComboFaithfully() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item()));
        when(itemModifierRepository.findByKey(ItemModifier.UNUSUAL)).thenReturn(Optional.of(unusualModifier()));
        when(unusualEffectRepository.findById(EFFECT_ID)).thenReturn(Optional.of(fiery()));

        UserItemResponse response = itemService.previewItem(
                ITEM_ID, EFFECT_ID, List.of(ItemModifier.UNUSUAL), "gold");

        assertThat(response.getLinkId()).isNull();
        assertThat(response.getItem().getName()).isEqualTo("Cool Title");
        assertThat(response.getQuantity()).isEqualTo(1L);
        assertThat(response.getVariantKey()).isEqualTo("gold");
        assertThat(response.getModifiers()).extracting(UserItemResponse.ModifierRef::getKey)
                .containsExactly(ItemModifier.UNUSUAL);
        assertThat(response.getUnusualEffect().getKey()).isEqualTo("fiery");
    }

    @Test
    void previewWithoutModifiersOrEffectReturnsBareItem() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item()));

        UserItemResponse response = itemService.previewItem(ITEM_ID, null, null, null);

        assertThat(response.getModifiers()).isEmpty();
        assertThat(response.getUnusualEffect()).isNull();
        assertThat(response.getVariantKey()).isNull();
    }

    @Test
    void previewRejectsEffectWithoutUnusualModifier() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item()));

        assertThatThrownBy(() -> itemService.previewItem(ITEM_ID, EFFECT_ID, null, null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void previewThrowsWhenItemMissing() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.previewItem(ITEM_ID, null, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void previewThrowsWhenEffectMissing() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item()));
        when(itemModifierRepository.findByKey(ItemModifier.UNUSUAL)).thenReturn(Optional.of(unusualModifier()));
        when(unusualEffectRepository.findById(EFFECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.previewItem(
                ITEM_ID, EFFECT_ID, List.of(ItemModifier.UNUSUAL), null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static Item item() {
        return Item.builder()
                .id(ITEM_ID)
                .type(ItemType.builder().id(UUID.randomUUID()).key("title").name("Title").build())
                .name("Cool Title")
                .rarity(ItemRarity.legendary)
                .worth(100L)
                .stackable(false)
                .tradeable(true)
                .build();
    }

    private static ItemModifier unusualModifier() {
        return ItemModifier.builder()
                .id(UUID.randomUUID())
                .key(ItemModifier.UNUSUAL)
                .name("Unusual")
                .build();
    }

    private static UnusualEffect fiery() {
        return UnusualEffect.builder()
                .id(EFFECT_ID)
                .key("fiery")
                .name("Fiery")
                .build();
    }
}
