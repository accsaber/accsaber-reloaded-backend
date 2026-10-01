package com.accsaber.backend.service.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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

import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanAuditAction;
import com.accsaber.backend.model.entity.clan.ClanAuditEntry;
import com.accsaber.backend.model.entity.clan.ClanEquippedItem;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.clan.ClanAuditEntryRepository;
import com.accsaber.backend.repository.clan.ClanEquippedItemRepository;
import com.accsaber.backend.repository.clan.ClanItemRepository;
import com.accsaber.backend.repository.item.ItemRepository;

@ExtendWith(MockitoExtension.class)
class ClanCosmeticServiceTest {

    private static final UUID CLAN_ID = UUID.randomUUID();
    private static final Long FOUNDER = 1L;

    @Mock
    private ClanItemRepository clanItemRepository;
    @Mock
    private ClanEquippedItemRepository equippedRepository;
    @Mock
    private ClanAuditEntryRepository auditRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private ClanRoster roster;
    @Mock
    private ClanAccessService accessService;
    @Mock
    private ClanRefCache refCache;

    @InjectMocks
    private ClanCosmeticService service;

    private final Clan clan = Clan.builder().id(CLAN_ID).build();
    private final User founder = User.builder().id(FOUNDER).build();
    private final ItemType clanCosmetic = ItemType.builder().id(UUID.randomUUID()).key("clan_cosmetic").build();

    @BeforeEach
    void setUp() {
        lenient().when(accessService.player(FOUNDER)).thenReturn(founder);
        lenient().when(roster.lock(CLAN_ID)).thenReturn(clan);
    }

    private Item item(ItemType parent, String typeKey) {
        ItemType type = ItemType.builder().id(UUID.randomUUID()).key(typeKey).parentType(parent).build();
        Item item = Item.builder().id(UUID.randomUUID()).name("Thing").type(type).build();
        when(itemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        return item;
    }

    @Test
    void onlyTheFounderEquips() {
        when(accessService.require(CLAN_ID, FOUNDER, ClanPermission.CUSTOMIZE)).thenThrow(new ForbiddenException());

        assertThatThrownBy(() -> service.equip(CLAN_ID, FOUNDER, UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aPlayerCosmeticCannotGoOnAClan() {
        Item title = item(null, "title");

        assertThatThrownBy(() -> service.equip(CLAN_ID, FOUNDER, title.getId()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void aCosmeticTheClanDoesNotOwnIsNotFound() {
        Item card = item(clanCosmetic, "clan_tag_card");

        assertThatThrownBy(() -> service.equip(CLAN_ID, FOUNDER, card.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(equippedRepository, never()).saveAndFlush(any());
    }

    @Test
    void equippingFillsTheSlotForTheItemTypeAndIsAudited() {
        Item card = item(clanCosmetic, "clan_tag_card");
        when(clanItemRepository.existsByClan_IdAndItem_Id(CLAN_ID, card.getId())).thenReturn(true);

        service.equip(CLAN_ID, FOUNDER, card.getId());

        ArgumentCaptor<ClanEquippedItem> slot = ArgumentCaptor.forClass(ClanEquippedItem.class);
        verify(equippedRepository).saveAndFlush(slot.capture());
        assertThat(slot.getValue().getItemType()).isSameAs(card.getType());
        assertThat(slot.getValue().getItem()).isSameAs(card);
        ArgumentCaptor<ClanAuditEntry> audit = ArgumentCaptor.forClass(ClanAuditEntry.class);
        verify(auditRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo(ClanAuditAction.cosmetic_equipped);
        assertThat(audit.getValue().getDetails()).containsEntry("itemType", "clan_tag_card");
        verify(refCache).refreshAfterCommit(CLAN_ID);
    }

    @Test
    void clearingAnEmptySlotWritesNoAudit() {
        service.unequip(CLAN_ID, FOUNDER, "clan_banner");

        verify(auditRepository, never()).save(any());
        verify(refCache, never()).refreshAfterCommit(any());
    }

    @Test
    void clearingAFilledSlotIsAudited() {
        when(equippedRepository.deleteSlot(CLAN_ID, "clan_banner")).thenReturn(1);

        service.unequip(CLAN_ID, FOUNDER, "clan_banner");

        verify(auditRepository).save(any(ClanAuditEntry.class));
        verify(refCache).refreshAfterCommit(CLAN_ID);
    }
}
