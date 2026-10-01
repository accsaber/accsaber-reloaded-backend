package com.accsaber.backend.service.clan;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.clan.ClanItemResponse;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.dto.response.item.ItemResponse;
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
import com.accsaber.backend.service.item.ItemMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClanCosmeticService {

    private final ClanItemRepository clanItemRepository;
    private final ClanEquippedItemRepository equippedRepository;
    private final ClanAuditEntryRepository auditRepository;
    private final ItemRepository itemRepository;
    private final ClanRoster roster;
    private final ClanAccessService accessService;
    private final ClanRefCache refCache;

    public Page<ClanItemResponse> owned(UUID clanId, Pageable pageable) {
        Set<UUID> equipped = equippedByClanIds(List.of(clanId)).getOrDefault(clanId, List.of()).stream()
                .map(ItemResponse::getId).collect(Collectors.toSet());
        return clanItemRepository.findPageByClanId(clanId, pageable)
                .map(owned -> new ClanItemResponse(ItemMapper.toItemResponse(owned.getItem()), owned.getSource(),
                        owned.getAcquiredAt(), equipped.contains(owned.getItem().getId())));
    }

    public Map<UUID, List<ItemResponse>> equippedByClanIds(Collection<UUID> clanIds) {
        if (clanIds.isEmpty()) {
            return Map.of();
        }
        return equippedRepository.findByClanIds(clanIds).stream()
                .collect(Collectors.groupingBy(e -> e.getClan().getId(),
                        Collectors.mapping(e -> ItemMapper.toItemResponse(e.getItem()), Collectors.toList())));
    }

    public Map<UUID, PublicClanResponse> publicRefs(Collection<Clan> clans) {
        Map<UUID, List<ItemResponse>> equipped = equippedByClanIds(clans.stream().map(Clan::getId).toList());
        return clans.stream().collect(Collectors.toMap(Clan::getId,
                clan -> PublicClanResponse.of(clan, equipped.getOrDefault(clan.getId(), List.of())),
                (first, second) -> first));
    }

    @Transactional
    public List<ItemResponse> equip(UUID clanId, Long playerId, UUID itemId) {
        User actor = accessService.player(playerId);
        Clan clan = roster.lock(clanId);
        accessService.require(clanId, actor.getId(), ClanPermission.CUSTOMIZE);
        Item item = itemRepository.findById(itemId).orElseThrow(() -> new ResourceNotFoundException("Item", itemId));
        ItemType type = item.getType();
        if (!type.isClanCosmetic()) {
            throw new ValidationException("itemId", "is not a clan cosmetic");
        }
        if (!clanItemRepository.existsByClan_IdAndItem_Id(clanId, itemId)) {
            throw new ResourceNotFoundException("ClanItem", itemId);
        }
        ClanEquippedItem slot = equippedRepository.findById(new ClanEquippedItem.Key(clanId, type.getId()))
                .orElseGet(() -> ClanEquippedItem.builder().clan(clan).itemType(type).build());
        slot.setItem(item);
        equippedRepository.saveAndFlush(slot);
        audit(clan, actor, type.getKey(), itemId);
        refCache.refreshAfterCommit(clanId);
        return equippedByClanIds(List.of(clanId)).getOrDefault(clanId, List.of());
    }

    @Transactional
    public void unequip(UUID clanId, Long playerId, String itemTypeKey) {
        User actor = accessService.player(playerId);
        Clan clan = roster.lock(clanId);
        accessService.require(clanId, actor.getId(), ClanPermission.CUSTOMIZE);
        if (equippedRepository.deleteSlot(clanId, itemTypeKey) > 0) {
            audit(clan, actor, itemTypeKey, null);
            refCache.refreshAfterCommit(clanId);
        }
    }

    private void audit(Clan clan, User actor, String itemTypeKey, UUID itemId) {
        Map<String, Object> details = itemId == null
                ? Map.<String, Object>of("itemType", itemTypeKey)
                : Map.<String, Object>of("itemType", itemTypeKey, "itemId", itemId.toString());
        auditRepository.save(ClanAuditEntry.builder().clan(clan).actor(actor)
                .action(ClanAuditAction.cosmetic_equipped).details(details).build());
    }
}
