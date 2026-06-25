package com.accsaber.backend.service.item;

import java.math.BigDecimal;
import java.util.Collection;
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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.item.InventoryFilter;
import com.accsaber.backend.model.dto.response.item.UserItemResponse;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemModifier;
import com.accsaber.backend.model.entity.item.ItemRarity;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.model.entity.user.UserSettingKey;
import com.accsaber.backend.repository.item.ItemModifierRepository;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.item.UserItemLinkCounterRepository;
import com.accsaber.backend.repository.item.UserItemLinkRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.player.UserSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ItemRepository itemRepository;
    private final UserItemLinkRepository userItemLinkRepository;
    private final UserRepository userRepository;
    private final DuplicateUserService duplicateUserService;
    private final ItemTypeService itemTypeService;
    private final UserSettingsService userSettingsService;
    private final ItemModifierRepository itemModifierRepository;
    private final UserItemLinkCounterRepository counterRepository;
    private final ModifierResolver modifierResolver;
    @PersistenceContext
    private EntityManager entityManager;
    private final ItemValueValidator itemValueValidator;

    public List<Item> findAllVisible() {
        return itemRepository.findByActiveTrueAndVisibleTrue();
    }

    public List<Item> findAllForStaff(boolean includeInactive) {
        return includeInactive
                ? itemRepository.findAll()
                : itemRepository.findByActiveTrue();
    }

    public List<Item> findByType(UUID typeId, boolean includeHidden) {
        return includeHidden
                ? itemRepository.findByType_IdAndActiveTrue(typeId)
                : itemRepository.findByType_IdAndActiveTrueAndVisibleTrue(typeId);
    }

    public List<Item> findByTypeForStaff(UUID typeId, boolean includeInactive) {
        return includeInactive
                ? itemRepository.findByType_Id(typeId)
                : itemRepository.findByType_IdAndActiveTrue(typeId);
    }

    public Item findById(UUID id) {
        return itemRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item", id));
    }

    public Item findByIdForStaff(UUID id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item", id));
    }

    public List<UserItemLink> findUserCollection(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        return userItemLinkRepository.findByUser_Id(resolved);
    }

    public List<UserItemLink> findUserCollectionByType(Long userId, String typeKey) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        return userItemLinkRepository.findByUser_IdAndItem_Type_Key(resolved, typeKey);
    }

    public Page<UserItemLink> findInventory(Long userId, InventoryFilter filter, Pageable pageable) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        InventoryFilter f = filter == null
                ? new InventoryFilter(null, null, null, null, null, null, null)
                : filter;
        return userItemLinkRepository.findInventoryFiltered(
                resolved,
                f.typeKeysOrNull(),
                f.raritiesOrNull(),
                f.modifierKeysOrNull(),
                f.tradeable(),
                f.sourcesOrNull(),
                f.deprecatedEffective(),
                f.searchOrNull(),
                resolveInventorySort(pageable));
    }

    private static final String RARITY_ORDER_EXPRESSION = buildRarityOrderExpression();

    private static String buildRarityOrderExpression() {
        StringBuilder sb = new StringBuilder("CASE l.item.rarity ");
        for (ItemRarity r : ItemRarity.values()) {
            sb.append("WHEN com.accsaber.backend.model.entity.item.ItemRarity.")
                    .append(r.name())
                    .append(" THEN ")
                    .append(r.ordinal())
                    .append(' ');
        }
        sb.append("END");
        return sb.toString();
    }

    private static Pageable resolveInventorySort(Pageable pageable) {
        if (!pageable.getSort().isSorted()) {
            return pageable;
        }
        Sort resolved = Sort.unsorted();
        for (Sort.Order order : pageable.getSort()) {
            if ("rarity".equals(order.getProperty())) {
                resolved = resolved.and(JpaSort.unsafe(order.getDirection(), RARITY_ORDER_EXPRESSION));
            } else {
                resolved = resolved.and(Sort.by(
                        new Sort.Order(order.getDirection(), order.getProperty(), Sort.NullHandling.NULLS_LAST)));
            }
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), resolved);
    }

    public Map<String, UserItemResponse> findEquippedHydrated(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);

        Map<String, Object> rawSettings = userSettingsService.getGroup(resolved, UserSettingKey.GROUP_EQUIPPED);

        Map<String, UUID> equippedLinkIdByType = new LinkedHashMap<>();
        for (UserSettingKey key : UserSettingKey.values()) {
            String typeKey = key.equippedTypeKey().orElse(null);
            if (typeKey == null)
                continue;
            Object raw = rawSettings.get(key.key());
            equippedLinkIdByType.put(typeKey, raw == null ? null : UUID.fromString(raw.toString()));
        }

        Set<String> needFallback = equippedLinkIdByType.entrySet().stream()
                .filter(e -> e.getValue() == null)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        Map<String, UserItemLink> fallbacksByType = new HashMap<>();
        if (!needFallback.isEmpty()) {
            userItemLinkRepository.findOwnedByTypeKeys(resolved, needFallback).stream()
                    .collect(Collectors.groupingBy(l -> l.getItem().getType().getKey()))
                    .forEach((typeKey, links) -> links.stream()
                            .max(Comparator.comparingInt(ItemService::tierRank)
                                    .thenComparing(UserItemLink::getAwardedAt))
                            .ifPresent(best -> fallbacksByType.put(typeKey, best)));
        }

        List<UUID> explicitIds = equippedLinkIdByType.values().stream().filter(Objects::nonNull).toList();
        Map<UUID, UserItemLink> explicitLinks = explicitIds.isEmpty()
                ? Map.of()
                : userItemLinkRepository.findAllById(explicitIds).stream()
                        .filter(l -> l.getUser().getId().equals(resolved))
                        .collect(Collectors.toMap(UserItemLink::getId, Function.identity()));

        Set<UUID> linkIds = new HashSet<>();
        equippedLinkIdByType.forEach((typeKey, linkId) -> {
            UserItemLink picked = linkId != null ? explicitLinks.get(linkId) : fallbacksByType.get(typeKey);
            if (picked != null)
                linkIds.add(picked.getId());
        });
        Map<UUID, Map<String, Long>> countersByLink = loadCounters(linkIds);

        Map<String, UserItemResponse> result = new LinkedHashMap<>();
        equippedLinkIdByType.forEach((typeKey, linkId) -> {
            UserItemLink picked = linkId != null ? explicitLinks.get(linkId) : fallbacksByType.get(typeKey);
            result.put(typeKey, picked == null
                    ? null
                    : ItemMapper.toUserItemResponse(picked, countersByLink.get(picked.getId())));
        });
        return result;
    }

    private Map<UUID, Map<String, Long>> loadCounters(Set<UUID> linkIds) {
        if (linkIds.isEmpty())
            return Map.of();
        Map<UUID, Map<String, Long>> grouped = new HashMap<>();
        for (var c : counterRepository.findByUserItemLink_IdIn(linkIds)) {
            grouped.computeIfAbsent(c.getId().getUserItemLinkId(), k -> new HashMap<>())
                    .put(c.getId().getStatKey(), c.getValue());
        }
        return grouped;
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

    @Transactional
    public Item create(UUID typeId, String name, String description, String iconUrl,
            Object value, ItemRarity rarity, boolean tradeable,
            boolean visible, boolean stackable, boolean welcomeGrant, boolean missionPoolable, boolean active,
            BigDecimal worth, String requirement, Integer unlockLevel) {
        ItemType type = itemTypeService.findByIdActive(typeId);
        itemValueValidator.validate(type, value);
        Item item = Item.builder()
                .type(type)
                .name(name)
                .description(description)
                .iconUrl(iconUrl)
                .value(toJsonNode(value))
                .rarity(rarity != null ? rarity : ItemRarity.common)
                .tradeable(tradeable)
                .visible(visible)
                .stackable(stackable)
                .welcomeGrant(welcomeGrant)
                .missionPoolable(missionPoolable)
                .active(active)
                .worth(worth)
                .requirement(requirement)
                .unlockLevel(unlockLevel)
                .build();
        return itemRepository.save(item);
    }

    @Transactional
    public Item update(UUID id, String name, String description, String iconUrl,
            Object value, ItemRarity rarity,
            Boolean tradeable, Boolean visible, Boolean stackable, Boolean welcomeGrant, Boolean missionPoolable,
            BigDecimal worth, String requirement, Integer unlockLevel) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item", id));
        if (name != null)
            item.setName(name);
        if (description != null)
            item.setDescription(description);
        if (iconUrl != null)
            item.setIconUrl(iconUrl);
        if (value != null) {
            itemValueValidator.validate(item.getType(), value);
            item.setValue(toJsonNode(value));
        }
        if (rarity != null)
            item.setRarity(rarity);
        if (tradeable != null)
            item.setTradeable(tradeable);
        if (visible != null)
            item.setVisible(visible);
        if (stackable != null)
            item.setStackable(stackable);
        if (welcomeGrant != null)
            item.setWelcomeGrant(welcomeGrant);
        if (missionPoolable != null)
            item.setMissionPoolable(missionPoolable);
        if (worth != null)
            item.setWorth(worth);
        if (requirement != null)
            item.setRequirement(requirement);
        if (unlockLevel != null)
            item.setUnlockLevel(unlockLevel);
        return itemRepository.save(item);
    }

    public List<ItemModifier> findAllActiveModifiers() {
        return itemModifierRepository.findByActiveTrue();
    }

    @Transactional
    public Item setIconUrl(UUID id, String iconUrl) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item", id));
        item.setIconUrl(iconUrl);
        return itemRepository.save(item);
    }

    @Transactional
    public void deactivate(UUID id) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item", id));
        item.setActive(false);
        itemRepository.save(item);
    }

    @Transactional
    public Item reactivate(UUID id) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item", id));
        item.setActive(true);
        return itemRepository.save(item);
    }

    @Transactional
    public void revokeAward(UUID linkId) {
        UserItemLink link = userItemLinkRepository.findById(linkId)
                .orElseThrow(() -> new ResourceNotFoundException("UserItemLink", linkId));
        Long ownerId = link.getUser().getId();
        String typeKey = link.getItem().getType().getKey();
        userItemLinkRepository.delete(link);
        userItemLinkRepository.flush();
        clearEquippedIfLinkGone(ownerId, linkId, typeKey);
    }

    @Transactional
    public void grantWelcomeItems(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        if (!userRepository.existsById(resolved))
            return;
        for (Item item : itemRepository.findByWelcomeGrantTrueAndActiveTrueAndDeprecatedFalse()) {
            if (userItemLinkRepository.existsByUser_IdAndItem_Id(resolved, item.getId()))
                continue;
            awardOrMerge(resolved, item, null, 1L, ItemSource.manual, "welcome", null, "Welcome grant");
        }
    }

    @Transactional
    public void awardSystem(Long userId, UUID itemId, ItemSource source, String sourceId, String reason) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        if (!userRepository.existsById(resolved))
            return;

        Item item = itemRepository.findByIdAndActiveTrue(itemId).orElse(null);
        if (item == null || item.isDeprecated())
            return;

        if (!item.isStackable()) {
            if (userItemLinkRepository.existsByUser_IdAndItem_IdAndSourceAndSourceId(
                    resolved, itemId, source, sourceId)) {
                return;
            }
            insertLink(resolved, item, Set.of(loadModifier(ItemModifier.NORMAL)),
                    null, 1L, source, sourceId, null, reason);
            return;
        }

        awardOrMerge(resolved, item, null, 1L, source, sourceId, null, reason);
    }

    @Transactional
    public UserItemLink awardFromCrate(Long userId, Item rewardItem, UUID consumedCrateLinkId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        if (!userRepository.existsById(resolved)) {
            throw new ResourceNotFoundException("User", userId);
        }
        if (rewardItem.isDeprecated()) {
            throw new ValidationException("rewardItem", "cannot award a deprecated item");
        }
        return awardOrMerge(resolved, rewardItem, null, 1L, ItemSource.crate_drop,
                consumedCrateLinkId.toString(), null, "Opened from crate");
    }

    @Transactional
    public UserItemLink awardManual(Long userId, UUID itemId, StaffUser staff, String reason,
            Collection<String> modifierKeys, Long quantity) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        if (!userRepository.existsById(resolved)) {
            throw new ResourceNotFoundException("User", userId);
        }
        Item item = itemRepository.findByIdAndActiveTrue(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item", itemId));
        if (item.isDeprecated()) {
            throw new ValidationException("itemId", "cannot award a deprecated item");
        }
        long qty = quantity == null ? 1L : quantity;
        if (qty < 1) {
            throw new ValidationException("quantity", "quantity must be at least 1");
        }
        if (!item.isStackable() && qty != 1) {
            throw new ValidationException("quantity", "non-stackable items can only be awarded one at a time");
        }

        Set<ItemModifier> explicit = (modifierKeys == null || modifierKeys.isEmpty())
                ? null
                : loadModifierSet(modifierKeys);

        return awardOrMerge(resolved, item, explicit, qty, ItemSource.manual, null, staff, reason);
    }

    @Transactional
    public Item deprecate(UUID itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item", itemId));
        if (item.isDeprecated())
            return item;
        item.setDeprecated(true);
        itemRepository.save(item);
        ItemModifier vintage = loadModifier(ItemModifier.VINTAGE);
        userItemLinkRepository.addModifierToAllLinksOfItem(itemId, vintage.getId());
        return item;
    }

    private UserItemLink awardOrMerge(Long userId, Item item, Set<ItemModifier> explicitModifiers, long quantity,
            ItemSource source, String sourceId, StaffUser staff, String reason) {
        boolean instanced = !item.isStackable() || hasPerInstanceModifier(explicitModifiers);

        if (!instanced) {
            Set<ItemModifier> modifiers = explicitModifiers != null
                    ? explicitModifiers
                    : Set.of(loadModifier(ItemModifier.NORMAL));
            UserItemLink existing = findStackableMatch(userId, item.getId(), modifiers);
            if (existing != null) {
                existing.setQuantity(existing.getQuantity() + quantity);
                return userItemLinkRepository.save(existing);
            }
            return insertLink(userId, item, modifiers, null, quantity, source, sourceId, staff, reason);
        }

        long serial = issueSerial(item.getId());
        Set<ItemModifier> modifiers = resolveInstancedModifiers(explicitModifiers, serial);
        return insertLink(userId, item, modifiers, serial, 1L, source, sourceId, staff, reason);
    }

    private Set<ItemModifier> resolveInstancedModifiers(Set<ItemModifier> explicit, long serial) {
        Set<ItemModifier> autoLayers = modifierResolver.resolveAutoLayers(serial).stream()
                .map(this::loadModifier)
                .collect(Collectors.toSet());
        if (explicit == null && autoLayers.isEmpty()) {
            return Set.of(loadModifier(ItemModifier.NORMAL));
        }
        Set<ItemModifier> combined = new HashSet<>();
        if (explicit != null)
            combined.addAll(explicit);
        combined.addAll(autoLayers);
        return combined;
    }

    private UserItemLink insertLink(Long userId, Item item, Set<ItemModifier> modifiers, Long serial, long quantity,
            ItemSource source, String sourceId, StaffUser staff, String reason) {
        UserItemLink link = UserItemLink.builder()
                .user(userRepository.getReferenceById(userId))
                .item(item)
                .modifiers(new HashSet<>(modifiers))
                .serialNumber(serial)
                .quantity(quantity)
                .source(source)
                .sourceId(sourceId)
                .awardedBy(staff)
                .reason(reason)
                .build();
        return userItemLinkRepository.save(link);
    }

    static boolean hasPerInstanceModifier(Set<ItemModifier> modifiers) {
        if (modifiers == null)
            return false;
        for (ItemModifier m : modifiers) {
            if (ItemModifier.PER_INSTANCE_KEYS.contains(m.getKey())) {
                return true;
            }
        }
        return false;
    }

    static boolean isInstanced(UserItemLink link) {
        return !link.getItem().isStackable() || hasPerInstanceModifier(link.getModifiers());
    }

    private UserItemLink findStackableMatch(Long userId, UUID itemId, Set<ItemModifier> modifiers) {
        Set<UUID> targetIds = modifiers.stream().map(ItemModifier::getId).collect(Collectors.toSet());
        return userItemLinkRepository.findByUser_IdAndItem_Id(userId, itemId).stream()
                .filter(l -> sameModifierSet(l.getModifiers(), targetIds))
                .findFirst()
                .orElse(null);
    }

    static boolean sameModifierSet(Set<ItemModifier> a, Set<UUID> b) {
        if (a.size() != b.size())
            return false;
        for (ItemModifier m : a) {
            if (!b.contains(m.getId()))
                return false;
        }
        return true;
    }

    private Set<ItemModifier> loadModifierSet(Collection<String> keys) {
        Set<ItemModifier> set = new HashSet<>();
        for (String key : keys) {
            set.add(loadModifier(key));
        }
        if (set.isEmpty()) {
            throw new ValidationException("modifierKeys", "at least one modifier is required");
        }
        return set;
    }

    private long issueSerial(UUID itemId) {
        Object result = entityManager.createNativeQuery(
                "UPDATE items SET next_serial = next_serial + 1 WHERE id = :id RETURNING next_serial - 1")
                .setParameter("id", itemId)
                .getSingleResult();
        if (result == null) {
            throw new ResourceNotFoundException("Item", itemId);
        }
        return ((Number) result).longValue();
    }

    private ItemModifier loadModifier(String key) {
        return itemModifierRepository.findByKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("ItemModifier", key));
    }

    @Transactional
    public void equip(Long userId, UUID linkId) {
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
    }

    @Transactional
    public void unequip(Long userId, String typeKey) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        UserSettingKey slot = UserSettingKey.forEquippedItemType(typeKey)
                .orElseThrow(() -> new ValidationException(
                        "typeKey", "items of type '" + typeKey + "' are not equippable"));
        userSettingsService.clear(resolved, slot);
    }

    @Transactional
    public void clearEquippedIfLinkGone(Long userId, UUID linkId, String typeKey) {
        UserSettingKey slot = UserSettingKey.forEquippedItemType(typeKey).orElse(null);
        if (slot == null)
            return;
        UUID equipped = userSettingsService.get(userId, slot, UUID.class);
        if (linkId.equals(equipped)) {
            userSettingsService.clear(userId, slot);
        }
    }

    private JsonNode toJsonNode(Object value) {
        if (value == null)
            return null;
        return MAPPER.valueToTree(value);
    }
}
