package com.accsaber.backend.service.item;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.model.entity.user.UserSettingKey;
import com.accsaber.backend.repository.item.UserItemLinkRepository;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.player.UserSettingsService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemEquipService {

    private static final Set<String> EQUIP_NO_FALLBACK_TYPES = Set.of("badge");

    private final UserItemLinkRepository userItemLinkRepository;
    private final DuplicateUserService duplicateUserService;
    private final UserSettingsService userSettingsService;

    public List<UserItemLink> findEffectiveEquippedLinks(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        Map<String, Object> rawSettings = userSettingsService.getGroup(resolved, UserSettingKey.GROUP_EQUIPPED);
        return resolveEquippedLinksByType(resolved, rawSettings).values().stream()
                .filter(Objects::nonNull)
                .toList();
    }

    Map<String, UserItemLink> resolveEquippedLinksByType(Long resolved, Map<String, Object> rawSettings) {
        Map<String, UUID> equippedLinkIdByType = new LinkedHashMap<>();
        for (UserSettingKey key : UserSettingKey.values()) {
            String typeKey = key.equippedTypeKey().orElse(null);
            if (typeKey == null)
                continue;
            equippedLinkIdByType.put(typeKey, parseLinkId(rawSettings.get(key.key())));
        }

        Set<String> needFallback = equippedLinkIdByType.entrySet().stream()
                .filter(e -> e.getValue() == null)
                .map(Map.Entry::getKey)
                .filter(typeKey -> !EQUIP_NO_FALLBACK_TYPES.contains(typeKey))
                .collect(Collectors.toSet());

        Map<String, UserItemLink> fallbacksByType = new HashMap<>();
        if (!needFallback.isEmpty()) {
            userItemLinkRepository.findOwnedByTypeKeys(resolved, needFallback).stream()
                    .collect(Collectors.groupingBy(l -> l.getItem().getType().getKey()))
                    .forEach((typeKey, links) -> links.stream()
                            .max(Comparator.comparingInt(ItemEquipService::tierRank)
                                    .thenComparing(UserItemLink::getAwardedAt))
                            .ifPresent(best -> fallbacksByType.put(typeKey, best)));
        }

        List<UUID> explicitIds = equippedLinkIdByType.values().stream().filter(Objects::nonNull).toList();
        Map<UUID, UserItemLink> explicitLinks = explicitIds.isEmpty()
                ? Map.of()
                : userItemLinkRepository.findAllById(explicitIds).stream()
                        .filter(l -> l.getUser().getId().equals(resolved))
                        .collect(Collectors.toMap(UserItemLink::getId, Function.identity()));

        Map<String, UserItemLink> picked = new LinkedHashMap<>();
        equippedLinkIdByType.forEach((typeKey, linkId) -> picked.put(typeKey,
                linkId != null ? explicitLinks.get(linkId) : fallbacksByType.get(typeKey)));
        return picked;
    }

    private static int tierRank(UserItemLink link) {
        if (link.getSource() == ItemSource.level && link.getSourceId() != null) {
            try {
                return Integer.parseInt(link.getSourceId());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static UUID parseLinkId(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    Set<UUID> equippedLinkIds(Long resolved) {
        Map<String, Object> rawSettings = userSettingsService.getGroup(resolved, UserSettingKey.GROUP_EQUIPPED);
        Set<UUID> ids = new HashSet<>();
        for (UserSettingKey key : UserSettingKey.values()) {
            if (key.equippedTypeKey().isEmpty()) {
                continue;
            }
            UUID linkId = parseLinkId(rawSettings.get(key.key()));
            if (linkId != null) {
                ids.add(linkId);
            }
        }
        return ids;
    }

    public boolean isLinkEquipped(Long userId, UUID linkId) {
        return equippedLinkIds(userId).contains(linkId);
    }

    @Transactional
    public void equip(Long userId, UUID linkId, String variantKey) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        UserItemLink link = userItemLinkRepository.findById(linkId)
                .orElseThrow(() -> new ResourceNotFoundException("UserItemLink", linkId));
        if (!link.getUser().getId().equals(resolved)) {
            throw new ValidationException("linkId", "user does not own this item link");
        }
        String typeKey = link.getItem().getType().getKey();
        UserSettingKey slot = UserSettingKey.forEquippedItemType(typeKey)
                .orElseThrow(() -> new ValidationException(
                        "linkId", "items of type '" + typeKey + "' are not equippable"));
        userSettingsService.set(resolved, slot, linkId);
        UserSettingKey variantSlot = UserSettingKey.forEquippedItemVariant(typeKey).orElse(null);
        if (variantSlot != null) {
            if (variantKey != null && !variantKey.isBlank()) {
                userSettingsService.set(resolved, variantSlot, variantKey);
            } else {
                userSettingsService.clear(resolved, variantSlot);
            }
        }
    }

    @Transactional
    public void unequip(Long userId, String typeKey) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        UserSettingKey slot = UserSettingKey.forEquippedItemType(typeKey)
                .orElseThrow(() -> new ValidationException(
                        "typeKey", "items of type '" + typeKey + "' are not equippable"));
        userSettingsService.clear(resolved, slot);
        UserSettingKey.forEquippedItemVariant(typeKey)
                .ifPresent(variantSlot -> userSettingsService.clear(resolved, variantSlot));
    }

    @Transactional
    public void clearEquippedIfLinkGone(Long userId, UUID linkId, String typeKey) {
        if (!isLinkEquipped(userId, linkId)) {
            return;
        }
        UserSettingKey.forEquippedItemType(typeKey)
                .ifPresent(slot -> userSettingsService.clear(userId, slot));
        UserSettingKey.forEquippedItemVariant(typeKey)
                .ifPresent(variantSlot -> userSettingsService.clear(userId, variantSlot));
    }
}
