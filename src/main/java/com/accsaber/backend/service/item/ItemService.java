package com.accsaber.backend.service.item;

import java.time.Instant;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.item.DisintegrateRequest;
import com.accsaber.backend.model.dto.request.item.InventoryFilter;
import com.accsaber.backend.model.dto.response.item.DisintegrationResponse;
import com.accsaber.backend.model.dto.response.item.ItemResponse;
import com.accsaber.backend.model.dto.response.item.UserItemResponse;
import com.accsaber.backend.model.entity.item.EssenceReason;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemModifier;
import com.accsaber.backend.model.entity.item.ItemRarity;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.model.entity.item.TradeStatus;
import com.accsaber.backend.model.entity.item.UnusualEffect;
import com.accsaber.backend.model.entity.item.UserItemCrateSource;
import com.accsaber.backend.model.entity.item.UserItemDisintegration;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.model.entity.notification.NotificationType;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.model.entity.user.UserSettingKey;
import com.accsaber.backend.repository.item.ItemModifierRepository;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.item.UnusualEffectRepository;
import com.accsaber.backend.repository.item.UserItemCrateSourceRepository;
import com.accsaber.backend.repository.item.UserItemDisintegrationRepository;
import com.accsaber.backend.repository.item.UserItemLinkCounterRepository;
import com.accsaber.backend.repository.item.UserItemLinkRepository;
import com.accsaber.backend.repository.item.UserItemTradeItemRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.notification.NotificationService;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.player.UserSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.ApplicationEventPublisher;

import com.accsaber.backend.model.event.InventoryChangedEvent;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> EQUIP_NO_FALLBACK_TYPES = Set.of("badge");

    private final ItemRepository itemRepository;
    private final UserItemLinkRepository userItemLinkRepository;
    private final UserRepository userRepository;
    private final DuplicateUserService duplicateUserService;
    private final ItemTypeService itemTypeService;
    private final UserSettingsService userSettingsService;
    private final ItemModifierRepository itemModifierRepository;
    private final UnusualEffectRepository unusualEffectRepository;
    private final UserItemLinkCounterRepository counterRepository;
    private final UserItemTradeItemRepository tradeItemRepository;
    private final UserItemDisintegrationRepository disintegrationRepository;
    private final UserItemCrateSourceRepository crateSourceRepository;
    private final ModifierResolver modifierResolver;
    @PersistenceContext
    private EntityManager entityManager;
    private final ItemValueValidator itemValueValidator;
    private final EssenceLedgerService essenceLedgerService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

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

    @Transactional(readOnly = true)
    public UserItemResponse previewItem(UUID itemId, UUID unusualEffectId, List<String> modifierKeys,
            String variantKey) {
        Item item = findByIdForStaff(itemId);
        Set<ItemModifier> modifiers = (modifierKeys == null || modifierKeys.isEmpty())
                ? Set.of()
                : loadModifierSet(modifierKeys);
        if (unusualEffectId != null && !hasModifier(modifiers, ItemModifier.UNUSUAL)) {
            throw new ValidationException("unusualEffectId",
                    "the unusual modifier must be applied to assign an unusual effect");
        }
        UnusualEffect effect = unusualEffectId == null
                ? null
                : unusualEffectRepository.findById(unusualEffectId)
                        .orElseThrow(() -> new ResourceNotFoundException("UnusualEffect", unusualEffectId));
        return ItemMapper.toPreviewResponse(item, modifiers, effect, variantKey);
    }

    private List<UserItemLink> findUserCollection(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        return userItemLinkRepository.findByUser_IdAndEscrowedFalse(resolved);
    }

    private List<UserItemLink> findUserCollectionByType(Long userId, String typeKey) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        return userItemLinkRepository.findByUser_IdAndItem_Type_KeyAndEscrowedFalse(resolved, typeKey);
    }

    private Page<UserItemLink> findInventory(Long userId, InventoryFilter filter, Pageable pageable) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        InventoryFilter f = filter == null
                ? new InventoryFilter(null, null, null, null, null, null, null, null)
                : filter;
        return userItemLinkRepository.findInventoryFiltered(
                resolved,
                f.typeKeysOrNull(),
                f.raritiesOrNull(),
                f.modifierKeysOrNull(),
                f.tradeable(),
                f.sourcesOrNull(),
                f.crateItemIdsOrNull(),
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

    private static final Map<String, String> INVENTORY_SORT_EXPRESSIONS = Map.of(
            "rarity", RARITY_ORDER_EXPRESSION,
            "crate", "ci.name");

    private static Pageable resolveInventorySort(Pageable pageable) {
        if (!pageable.getSort().isSorted()) {
            return pageable;
        }
        Sort resolved = Sort.unsorted();
        for (Sort.Order order : pageable.getSort()) {
            String expression = INVENTORY_SORT_EXPRESSIONS.get(order.getProperty());
            resolved = resolved.and(expression == null
                    ? Sort.by(new Sort.Order(order.getDirection(), order.getProperty(), Sort.NullHandling.NULLS_LAST))
                    : unsafeNullsLast(order.getDirection(), expression));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), resolved);
    }

    private static Sort unsafeNullsLast(Sort.Direction direction, String expression) {
        return Sort.by(JpaSort.unsafe(direction, expression).stream()
                .map(order -> order.with(Sort.NullHandling.NULLS_LAST))
                .toList());
    }

    public Map<String, UserItemResponse> findEquippedHydrated(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);

        Map<String, Object> rawSettings = userSettingsService.getGroup(resolved, UserSettingKey.GROUP_EQUIPPED);
        Map<String, UserItemLink> pickedByType = resolveEquippedLinksByType(resolved, rawSettings);

        Map<UUID, Map<String, Long>> countersByLink = counterRepository
                .countersByLink(linkIds(pickedByType.values()));

        Map<String, UserItemResponse> result = new LinkedHashMap<>();
        pickedByType.forEach((typeKey, picked) -> {
            UserItemResponse response = picked == null ? null : hydrate(picked, countersByLink, Map.of());
            boolean explicit = UserSettingKey.forEquippedItemType(typeKey)
                    .map(slot -> rawSettings.get(slot.key()) != null)
                    .orElse(false);
            if (response != null && explicit) {
                UserSettingKey.forEquippedItemVariant(typeKey).ifPresent(variantSlot -> {
                    Object variant = rawSettings.get(variantSlot.key());
                    if (variant != null) {
                        response.setVariantKey(variant.toString());
                    }
                });
            }
            result.put(typeKey, response);
        });
        return result;
    }

    public List<UserItemResponse> findUserCollectionHydrated(Long userId, String typeKey) {
        return hydrateAll(typeKey == null
                ? findUserCollection(userId)
                : findUserCollectionByType(userId, typeKey));
    }

    public List<ItemResponse> findInventoryCrates(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        return userItemLinkRepository.findInventoryCrates(resolved).stream()
                .map(ItemMapper::toItemResponse)
                .toList();
    }

    public Page<UserItemResponse> findInventoryHydrated(Long userId, InventoryFilter filter, Pageable pageable) {
        Page<UserItemLink> page = findInventory(userId, filter, pageable);
        Map<UUID, Map<String, Long>> countersByLink = counterRepository
                .countersByLink(linkIds(page.getContent()));
        Map<String, ItemResponse> cratesBySourceId = cratesBySourceId(page.getContent());
        return page.map(link -> hydrate(link, countersByLink, cratesBySourceId));
    }

    private List<UserItemResponse> hydrateAll(List<UserItemLink> links) {
        Map<UUID, Map<String, Long>> countersByLink = counterRepository.countersByLink(linkIds(links));
        Map<String, ItemResponse> cratesBySourceId = cratesBySourceId(links);
        return links.stream()
                .map(link -> hydrate(link, countersByLink, cratesBySourceId))
                .toList();
    }

    private Map<String, ItemResponse> cratesBySourceId(Collection<UserItemLink> links) {
        Set<String> sourceIds = links.stream()
                .filter(ItemService::fromCrate)
                .map(UserItemLink::getSourceId)
                .collect(Collectors.toSet());
        if (sourceIds.isEmpty()) {
            return Map.of();
        }
        return crateSourceRepository.findAllWithCrateItem(sourceIds).stream()
                .collect(Collectors.toMap(UserItemCrateSource::getSourceId,
                        source -> ItemMapper.toItemResponse(source.getCrateItem())));
    }

    private static boolean fromCrate(UserItemLink link) {
        return link != null && link.getSource() == ItemSource.crate_drop && link.getSourceId() != null;
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

    private static UserItemResponse hydrate(UserItemLink link, Map<UUID, Map<String, Long>> countersByLink,
            Map<String, ItemResponse> cratesBySourceId) {
        UserItemResponse response = ItemMapper.toUserItemResponse(link, countersByLink.get(link.getId()));
        if (fromCrate(link)) {
            response.setCrate(cratesBySourceId.get(link.getSourceId()));
        }
        return response;
    }

    private static Set<UUID> linkIds(Collection<UserItemLink> links) {
        return links.stream()
                .filter(Objects::nonNull)
                .map(UserItemLink::getId)
                .collect(Collectors.toSet());
    }

    public List<UserItemLink> findEffectiveEquippedLinks(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        Map<String, Object> rawSettings = userSettingsService.getGroup(resolved, UserSettingKey.GROUP_EQUIPPED);
        return resolveEquippedLinksByType(resolved, rawSettings).values().stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<String, UserItemLink> resolveEquippedLinksByType(Long resolved, Map<String, Object> rawSettings) {
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

    @Transactional
    public Item create(UUID typeId, String name, String description, String iconUrl,
            Object value, ItemRarity rarity, boolean tradeable,
            boolean visible, boolean stackable, boolean welcomeGrant, boolean missionPoolable, boolean downloadable,
            boolean uniquePerUser, boolean serialized, boolean active, Long worth, String requirement,
            Integer unlockLevel) {
        ItemType type = itemTypeService.findByIdActive(typeId);
        if (itemRepository.existsByType_IdAndName(typeId, name)) {
            throw new ConflictException("An item named '" + name + "' already exists for this type");
        }
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
                .downloadable(downloadable)
                .uniquePerUser(uniquePerUser)
                .serialized(serialized)
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
            Boolean downloadable, Boolean uniquePerUser, Boolean serialized, Long worth, String requirement,
            Integer unlockLevel) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item", id));
        if (name != null && !name.equals(item.getName())) {
            if (itemRepository.existsByType_IdAndName(item.getType().getId(), name)) {
                throw new ConflictException("An item named '" + name + "' already exists for this type");
            }
            item.setName(name);
        }
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
        if (downloadable != null)
            item.setDownloadable(downloadable);
        if (uniquePerUser != null)
            item.setUniquePerUser(uniquePerUser);
        if (serialized != null)
            item.setSerialized(serialized);
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
    public ItemModifier updateModifier(UUID id, Double globalDropChance,
            String seasonStart, String seasonEnd) {
        ItemModifier modifier = itemModifierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ItemModifier", id));
        validateGlobalDropConfig(globalDropChance, seasonStart, seasonEnd);
        modifier.setGlobalDropChance(globalDropChance);
        modifier.setSeasonStart(seasonStart);
        modifier.setSeasonEnd(seasonEnd);
        return itemModifierRepository.save(modifier);
    }

    private void validateGlobalDropConfig(Double globalDropChance, String seasonStart, String seasonEnd) {
        if (globalDropChance != null
                && (Math.signum(globalDropChance) <= 0 || globalDropChance.compareTo(1.0) > 0)) {
            throw new ValidationException("globalDropChance", "must be between 0 (exclusive) and 1 (inclusive)");
        }
        if ((seasonStart == null) != (seasonEnd == null)) {
            throw new ValidationException("season", "seasonStart and seasonEnd must both be set or both be null");
        }
        validateMonthDay("seasonStart", seasonStart);
        validateMonthDay("seasonEnd", seasonEnd);
    }

    private void validateMonthDay(String field, String value) {
        if (value == null) {
            return;
        }
        try {
            ModifierResolver.parseSeasonBound(value);
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            throw new ValidationException(field, "must be a valid MM-DD date");
        }
    }

    @Transactional
    public Item setIconUrl(UUID id, String iconUrl) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item", id));
        item.setIconUrl(iconUrl);
        return itemRepository.save(item);
    }

    @Transactional
    public Item setUploadedImage(UUID id, String url) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item", id));
        item.setIconUrl(url);
        JsonNode value = item.getValue();
        if (value != null && value.path("asset").isObject()) {
            ObjectNode root = ((ObjectNode) value).deepCopy();
            ObjectNode asset = (ObjectNode) root.get("asset");
            ObjectNode raster = asset.path("raster").isObject()
                    ? (ObjectNode) asset.get("raster")
                    : asset.putObject("raster");
            raster.put("1x", url);
            if (!asset.path("altText").isTextual()) {
                asset.put("altText", item.getName());
            }
            item.setValue(root);
        }
        return itemRepository.save(item);
    }

    @Transactional
    public Item setActive(UUID id, boolean active) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item", id));
        item.setActive(active);
        return itemRepository.save(item);
    }

    @Transactional
    public Item setObtainableUntil(UUID id, Instant obtainableUntil) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item", id));
        item.setObtainableUntil(obtainableUntil);
        return itemRepository.save(item);
    }

    public long getEssenceBalance(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        return essenceLedgerService.balance(resolved);
    }

    public long getReservedEssence(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        return essenceLedgerService.reserved(resolved);
    }

    @Transactional
    public DisintegrationResponse disintegrate(Long userId, List<DisintegrateRequest.Entry> requested) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        Map<UUID, Long> requestedByLink = requestedQuantities(requested);
        List<UserItemLink> links = lockForDisintegration(resolved, requestedByLink);
        Set<UUID> equipped = equippedLinkIds(resolved);
        Set<UUID> consumed = new HashSet<>();

        List<DisintegrationResponse.Entry> entries = links.stream()
                .map(link -> consume(link, requestedByLink.get(link.getId()), equipped, consumed))
                .toList();
        applyDisintegration(resolved, links, consumed, entries);

        return DisintegrationResponse.builder()
                .entries(entries)
                .essenceGained(entries.stream().mapToLong(DisintegrationResponse.Entry::getEssenceGained).sum())
                .balance(essenceLedgerService.balance(resolved))
                .build();
    }

    private static Map<UUID, Long> requestedQuantities(List<DisintegrateRequest.Entry> requested) {
        Map<UUID, Long> byLink = new LinkedHashMap<>();
        for (DisintegrateRequest.Entry entry : requested) {
            if (byLink.containsKey(entry.getLinkId())) {
                throw new ValidationException("entries", "the same item is listed more than once");
            }
            byLink.put(entry.getLinkId(), entry.getQuantity());
        }
        if (byLink.isEmpty()) {
            throw new ValidationException("entries", "pick at least one item to disintegrate");
        }
        return byLink;
    }

    private List<UserItemLink> lockForDisintegration(Long resolved, Map<UUID, Long> requestedByLink) {
        List<UserItemLink> links = userItemLinkRepository.findAllByIdForUpdate(requestedByLink.keySet());
        Set<UUID> found = links.stream().map(UserItemLink::getId).collect(Collectors.toSet());
        requestedByLink.keySet().stream()
                .filter(linkId -> !found.contains(linkId))
                .findFirst()
                .ifPresent(linkId -> {
                    throw new ResourceNotFoundException("UserItemLink", linkId);
                });
        if (links.stream().anyMatch(link -> !link.getUser().getId().equals(resolved))) {
            throw new ValidationException("entries", "one of these items is not yours");
        }
        if (!tradeItemRepository.findLinkIdsInTradesWithStatus(requestedByLink.keySet(), TradeStatus.pending)
                .isEmpty()) {
            throw new ConflictException("An item in this selection is part of a pending trade "
                    + "and cannot be disintegrated");
        }
        return links;
    }

    private DisintegrationResponse.Entry consume(UserItemLink link, Long requestedQuantity, Set<UUID> equipped,
            Set<UUID> consumed) {
        long quantity = resolveDestroyQuantity(link, requestedQuantity, equipped);
        long remaining = link.getQuantity() - quantity;
        if (remaining == 0) {
            consumed.add(link.getId());
        } else {
            link.setQuantity(remaining);
        }
        return DisintegrationResponse.Entry.builder()
                .linkId(link.getId())
                .itemId(link.getItem().getId())
                .quantityDisintegrated(quantity)
                .remainingQuantity(remaining == 0 ? null : remaining)
                .essenceGained(link.getItem().getWorth() * quantity)
                .build();
    }

    private long resolveDestroyQuantity(UserItemLink link, Long requestedQuantity, Set<UUID> equipped) {
        Item item = link.getItem();
        if (!item.isTradeable()) {
            throw new ValidationException("entries",
                    item.getName() + " is not tradeable and cannot be disintegrated");
        }
        if (item.getWorth() == null || item.getWorth() <= 0) {
            throw new ValidationException("entries",
                    item.getName() + " has no essence value and cannot be disintegrated");
        }
        if (link.isEscrowed()) {
            throw new ConflictException(item.getName() + " is listed on the market and cannot be disintegrated");
        }
        if (equipped.contains(link.getId())) {
            throw new ConflictException("Unequip " + item.getName() + " before disintegrating it");
        }
        long owned = link.getQuantity();
        long quantity = requestedQuantity == null ? owned : requestedQuantity;
        if (quantity < 1 || quantity > owned) {
            throw new ValidationException("entries",
                    "quantity for " + item.getName() + " must be between 1 and the " + owned + " you own");
        }
        return quantity;
    }

    private void applyDisintegration(Long resolved, List<UserItemLink> links, Set<UUID> consumed,
            List<DisintegrationResponse.Entry> entries) {
        userItemLinkRepository.saveAll(links.stream()
                .filter(link -> !consumed.contains(link.getId()))
                .toList());
        if (!consumed.isEmpty()) {
            userItemLinkRepository.deleteAllByIdInBatch(consumed);
        }
        userItemLinkRepository.flush();
        essenceLedgerService.creditAll(resolved, EssenceReason.disintegration, entries.stream()
                .collect(Collectors.toMap(DisintegrationResponse.Entry::getLinkId,
                        DisintegrationResponse.Entry::getEssenceGained)));
        disintegrationRepository.saveAll(disintegrationRecords(resolved, links, entries));
        eventPublisher.publishEvent(new InventoryChangedEvent(resolved));
    }

    private List<UserItemDisintegration> disintegrationRecords(Long resolved, List<UserItemLink> links,
            List<DisintegrationResponse.Entry> entries) {
        var user = userRepository.getReferenceById(resolved);
        return IntStream.range(0, links.size())
                .mapToObj(index -> UserItemDisintegration.builder()
                        .user(user)
                        .item(links.get(index).getItem())
                        .quantity(entries.get(index).getQuantityDisintegrated())
                        .essenceGained(entries.get(index).getEssenceGained())
                        .build())
                .toList();
    }

    private Set<UUID> equippedLinkIds(Long resolved) {
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
            Item granted = resolveGrantItem(item);
            if (granted == null || userItemLinkRepository.existsByUser_IdAndItem_Id(resolved, granted.getId()))
                continue;
            awardOrMerge(resolved, granted, null, 1L, ItemSource.welcome, null, null, "Welcome grant");
        }
    }

    public static boolean isActiveCrateSentinel(Item item) {
        JsonNode value = item.getValue();
        return value != null && "active_crate".equals(value.path("grant").asText(null));
    }

    Item resolveGrantItem(Item item) {
        if (!isActiveCrateSentinel(item)) {
            return item;
        }
        List<Item> crates = itemRepository
                .findByType_KeyAndActiveTrueAndDeprecatedFalseAndVisibleTrue("crate").stream()
                .filter(c -> !isActiveCrateSentinel(c))
                .toList();
        Instant now = Instant.now();
        List<Item> inWindow = crates.stream().filter(c -> c.isObtainableAt(now)).toList();
        if (!inWindow.isEmpty()) {
            return inWindow.get(ThreadLocalRandom.current().nextInt(inWindow.size()));
        }
        return crates.stream()
                .max(Comparator.comparing(Item::getCreatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    @Transactional
    public void awardSystem(Long userId, UUID itemId, ItemSource source, String sourceId, String reason) {
        awardSystem(userId, itemId, source, sourceId, reason, 1);
    }

    @Transactional
    public void awardSystem(Long userId, UUID itemId, ItemSource source, String sourceId, String reason,
            int quantity) {
        if (quantity < 1)
            return;

        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        if (!userRepository.existsById(resolved))
            return;

        Item requested = itemRepository.findByIdAndActiveTrue(itemId).orElse(null);
        if (requested == null || requested.isDeprecated())
            return;
        Item item = resolveGrantItem(requested);
        if (item == null)
            return;

        if (!item.isStackable()) {
            if (item.isUniquePerUser() && userItemLinkRepository.existsByUser_IdAndItem_Id(resolved, item.getId())) {
                return;
            }
            long target = item.isUniquePerUser() ? 1L : quantity;
            long shortfall = target - countPriorGrants(resolved, requested, item, source, sourceId);
            if (shortfall < 1)
                return;
            awardOrMerge(resolved, item, null, shortfall, source, sourceId, null, reason);
            return;
        }

        awardOrMerge(resolved, item, null, quantity, source, sourceId, null, reason);
    }

    private long countPriorGrants(Long userId, Item requested, Item granted, ItemSource source, String sourceId) {
        if (requested == granted) {
            return userItemLinkRepository.countByUser_IdAndItem_IdAndSourceAndSourceId(
                    userId, granted.getId(), source, sourceId);
        }
        return userItemLinkRepository.countByUser_IdAndItem_Type_KeyAndSourceAndSourceId(
                userId, granted.getType().getKey(), source, sourceId);
    }

    @Transactional
    public UserItemLink awardFromCrate(Long userId, Item rewardItem, UUID consumedCrateLinkId,
            Set<ItemModifier> rolledModifiers, UnusualEffect unusualEffect) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        if (!userRepository.existsById(resolved)) {
            throw new ResourceNotFoundException("User", userId);
        }
        if (rewardItem.isDeprecated()) {
            throw new ValidationException("rewardItem", "cannot award a deprecated item");
        }
        Set<ItemModifier> explicit = (rolledModifiers == null || rolledModifiers.isEmpty())
                ? null
                : rolledModifiers;
        UserItemLink link = awardOrMerge(resolved, rewardItem, explicit, 1L, ItemSource.crate_drop,
                consumedCrateLinkId.toString(), null, "Opened from crate");
        if (unusualEffect != null) {
            link.setUnusualEffect(unusualEffect);
            link = userItemLinkRepository.save(link);
        }
        return link;
    }

    @Transactional
    public UserItemLink awardManual(Long userId, UUID itemId, StaffUser staff, String reason,
            Collection<String> modifierKeys, Long quantity, UUID unusualEffectId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        if (!userRepository.existsById(resolved)) {
            throw new ResourceNotFoundException("User", userId);
        }
        Item requested = itemRepository.findByIdAndActiveTrue(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item", itemId));
        if (requested.isDeprecated()) {
            throw new ValidationException("itemId", "cannot award a deprecated item");
        }
        Item item = resolveGrantItem(requested);
        if (item == null) {
            throw new ValidationException("itemId", "no active crate is available to grant right now");
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

        if (unusualEffectId != null && !hasModifier(explicit, ItemModifier.UNUSUAL)) {
            throw new ValidationException("unusualEffectId",
                    "the unusual modifier must be applied to assign an unusual effect");
        }

        UserItemLink link = awardOrMerge(resolved, item, explicit, qty, ItemSource.manual, null, staff, reason);

        if (unusualEffectId != null) {
            UnusualEffect effect = unusualEffectRepository.findById(unusualEffectId)
                    .orElseThrow(() -> new ResourceNotFoundException("UnusualEffect", unusualEffectId));
            link.setUnusualEffect(effect);
            link = userItemLinkRepository.save(link);
        }

        return link;
    }

    private static boolean hasModifier(Set<ItemModifier> modifiers, String key) {
        return modifiers != null && modifiers.stream().anyMatch(m -> key.equals(m.getKey()));
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
        if (item.getType().isClanCosmetic()) {
            throw new ValidationException("itemId", "is a clan cosmetic and only a clan can own it");
        }
        if (item.isUniquePerUser() && userItemLinkRepository.existsByUser_IdAndItem_Id(userId, item.getId())) {
            throw new ConflictException("Player already owns '" + item.getName() + "', which is a unique item");
        }
        boolean instanced = !item.isStackable() || hasPerInstanceModifier(explicitModifiers);

        if (!instanced) {
            Set<ItemModifier> modifiers = explicitModifiers != null
                    ? explicitModifiers
                    : Set.of(loadModifier(ItemModifier.NORMAL));
            UserItemLink existing = findStackableMatch(userId, item.getId(), modifiers);
            if (existing != null) {
                existing.setQuantity(existing.getQuantity() + quantity);
                reattribute(existing, source, sourceId, staff, reason);
                UserItemLink merged = userItemLinkRepository.save(existing);
                notifyItemEarned(userId, merged, quantity, source);
                return merged;
            }
            return insertLink(userId, item, modifiers, null, quantity, source, sourceId, staff, reason);
        }

        return insertInstancedCopies(userId, item, explicitModifiers, quantity, source, sourceId, staff, reason);
    }

    private UserItemLink insertInstancedCopies(Long userId, Item item, Set<ItemModifier> explicitModifiers,
            long quantity, ItemSource source, String sourceId, StaffUser staff, String reason) {
        UserItemLink last = null;
        for (long i = 0; i < quantity; i++) {
            Long serial = item.isSerialized() ? issueSerial(item.getId()) : null;
            Set<ItemModifier> modifiers = serial != null
                    ? resolveInstancedModifiers(explicitModifiers, serial)
                    : (explicitModifiers != null ? explicitModifiers : Set.of(loadModifier(ItemModifier.NORMAL)));
            last = createLink(userId, item, modifiers, serial, 1L, source, sourceId, staff, reason);
        }
        notifyItemEarned(userId, last, quantity, source);
        return last;
    }

    private Set<ItemModifier> resolveInstancedModifiers(Set<ItemModifier> explicit, long serial) {
        Set<ItemModifier> autoLayers = modifierResolver.resolveFounders(serial).stream()
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
        UserItemLink saved = createLink(userId, item, modifiers, serial, quantity, source, sourceId, staff, reason);
        notifyItemEarned(userId, saved, quantity, source);
        return saved;
    }

    private UserItemLink createLink(Long userId, Item item, Set<ItemModifier> modifiers, Long serial, long quantity,
            ItemSource source, String sourceId, StaffUser staff, String reason) {
        if (isActiveCrateSentinel(item)) {
            throw new ValidationException("item",
                    "this item resolves to an active crate at grant time and can never be owned directly");
        }
        if (item.isUniquePerUser() && userItemLinkRepository.existsByUser_IdAndItem_Id(userId, item.getId())) {
            throw new ConflictException("Player already owns '" + item.getName() + "', which is a unique item");
        }
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

    private void notifyItemEarned(Long userId, UserItemLink link, long quantity, ItemSource source) {
        String name = link.getItem().getName();
        String title = quantity > 1
                ? "You received " + quantity + "x " + name + "!"
                : "You received " + name + "!";
        notificationService.notify(userId, NotificationType.item_earned, null, title,
                "/players/" + userId + "?inventoryHighlight=" + link.getId());
    }

    static void reattribute(UserItemLink link, ItemSource source, String sourceId, StaffUser staff, String reason) {
        link.setSource(source);
        link.setSourceId(sourceId);
        link.setAwardedBy(staff);
        link.setReason(reason);
        link.setAwardedAt(Instant.now());
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
        return !link.getItem().isStackable()
                || link.getSerialNumber() != null
                || link.getUnusualEffect() != null
                || hasPerInstanceModifier(link.getModifiers());
    }

    private UserItemLink findStackableMatch(Long userId, UUID itemId, Set<ItemModifier> modifiers) {
        Set<UUID> targetIds = modifiers.stream().map(ItemModifier::getId).collect(Collectors.toSet());
        return userItemLinkRepository.findByUser_IdAndItem_IdAndEscrowedFalse(userId, itemId).stream()
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

    private JsonNode toJsonNode(Object value) {
        if (value == null)
            return null;
        return MAPPER.valueToTree(value);
    }
}
