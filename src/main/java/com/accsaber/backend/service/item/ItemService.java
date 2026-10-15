package com.accsaber.backend.service.item;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.item.CreateItemRequest;
import com.accsaber.backend.model.dto.request.item.InventoryFilter;
import com.accsaber.backend.model.dto.request.item.UpdateItemRequest;
import com.accsaber.backend.model.dto.response.item.ItemResponse;
import com.accsaber.backend.model.dto.response.item.UserItemResponse;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemModifier;
import com.accsaber.backend.model.entity.item.ItemRarity;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.model.entity.item.TradeStatus;
import com.accsaber.backend.model.entity.item.UnusualEffect;
import com.accsaber.backend.model.entity.item.UserItemCrateSource;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.model.entity.user.UserSettingKey;
import com.accsaber.backend.repository.SortAliases;
import com.accsaber.backend.repository.item.ItemModifierRepository;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.item.UnusualEffectRepository;
import com.accsaber.backend.repository.item.UserItemCrateSourceRepository;
import com.accsaber.backend.repository.item.UserItemLinkCounterRepository;
import com.accsaber.backend.repository.item.UserItemLinkRepository;
import com.accsaber.backend.repository.item.UserItemTradeItemRepository;
import com.accsaber.backend.service.media.MediaProcessingService;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.player.UserSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {

    private static final String ICON_SUBDIR = "items";

    private final ItemRepository itemRepository;
    private final UserItemLinkRepository userItemLinkRepository;
    private final DuplicateUserService duplicateUserService;
    private final ItemTypeService itemTypeService;
    private final UserSettingsService userSettingsService;
    private final ItemModifierRepository itemModifierRepository;
    private final UnusualEffectRepository unusualEffectRepository;
    private final UserItemLinkCounterRepository counterRepository;
    private final UserItemTradeItemRepository tradeItemRepository;
    private final UserItemCrateSourceRepository crateSourceRepository;
    private final ItemValueValidator itemValueValidator;
    private final ItemAwardService itemAwardService;
    private final ItemEquipService itemEquipService;
    private final MediaProcessingService mediaProcessingService;
    private final TransactionTemplate transactionTemplate;

    public List<Item> findPlayerCatalogue() {
        return itemRepository.findVisiblePlayerItems();
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
                : itemAwardService.loadModifierSet(modifierKeys);
        if (unusualEffectId != null && !ItemAwardService.hasModifier(modifiers, ItemModifier.UNUSUAL)) {
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

    private Page<Object[]> findInventory(Long userId, InventoryFilter filter, Pageable pageable) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        InventoryFilter f = filter == null
                ? new InventoryFilter(null, null, null, null, null, null, null, null, false)
                : filter;
        return userItemLinkRepository.findInventoryFiltered(
                resolved,
                f.includeUnowned(),
                f.typeKeysOrNull(),
                f.raritiesOrNull(),
                f.modifierKeysOrNull(),
                f.tradeable(),
                f.sourcesOrNull(),
                f.crateItemIdsOrNull(),
                f.deprecatedEffective(),
                f.searchOrNull(),
                SortAliases.translateEach(pageable, INVENTORY_SORT_EXPRESSIONS));
    }

    private static final String RARITY_ORDER_EXPRESSION = buildRarityOrderExpression();

    private static String buildRarityOrderExpression() {
        StringBuilder sb = new StringBuilder("CASE i.rarity ");
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
            "crate", "ci.name",
            "awardedAt", "l.awardedAt",
            "serialNumber", "l.serialNumber",
            "quantity", "l.quantity",
            "item.name", "i.name",
            "item.type.key", "t.key");

    public Map<String, UserItemResponse> findEquippedHydrated(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);

        Map<String, Object> rawSettings = userSettingsService.getGroup(resolved, UserSettingKey.GROUP_EQUIPPED);
        Map<String, UserItemLink> pickedByType = itemEquipService.resolveEquippedLinksByType(resolved, rawSettings);

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
        Page<Object[]> page = findInventory(userId, filter, pageable);
        List<UserItemLink> links = page.getContent().stream()
                .map(row -> (UserItemLink) row[1])
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, Map<String, Long>> countersByLink = counterRepository.countersByLink(linkIds(links));
        Map<String, ItemResponse> cratesBySourceId = cratesBySourceId(links);
        Set<UUID> pendingTradeLinks = links.isEmpty() ? Set.of()
                : Set.copyOf(tradeItemRepository.findLinkIdsInTradesWithStatus(linkIds(links), TradeStatus.pending));
        return page.map(row -> row[1] instanceof UserItemLink link
                ? withPendingTrade(hydrate(link, countersByLink, cratesBySourceId), pendingTradeLinks)
                : UserItemResponse.builder()
                        .item(ItemMapper.toItemResponse((Item) row[0]))
                        .modifiers(List.of())
                        .quantity(0L)
                        .build());
    }

    private static UserItemResponse withPendingTrade(UserItemResponse response, Set<UUID> pendingTradeLinks) {
        response.setPendingTrade(pendingTradeLinks.contains(response.getLinkId()));
        return response;
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

    @Transactional
    public Item create(CreateItemRequest req) {
        ItemType type = itemTypeService.findByIdActive(req.getTypeId());
        if (itemRepository.existsByType_IdAndName(req.getTypeId(), req.getName())) {
            throw new ConflictException("An item named '" + req.getName() + "' already exists for this type");
        }
        itemValueValidator.validate(type, req.getValue());
        Item item = Item.builder()
                .type(type)
                .name(req.getName())
                .description(req.getDescription())
                .iconUrl(req.getIconUrl())
                .value(ItemMapper.toJsonNode(req.getValue()))
                .rarity(req.getRarity() != null ? req.getRarity() : ItemRarity.common)
                .tradeable(req.isTradeable())
                .visible(req.isVisible())
                .stackable(req.isStackable())
                .welcomeGrant(req.isWelcomeGrant())
                .missionPoolable(req.isMissionPoolable())
                .downloadable(req.isDownloadable())
                .uniquePerUser(req.isUniquePerUser())
                .serialized(req.isSerialized())
                .active(req.isActive())
                .worth(req.getWorth())
                .requirement(req.getRequirement())
                .unlockLevel(req.getUnlockLevel())
                .build();
        return itemRepository.save(item);
    }

    @Transactional
    public Item update(UUID id, UpdateItemRequest req) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item", id));
        String name = req.getName();
        if (name != null && !name.equals(item.getName())) {
            if (itemRepository.existsByType_IdAndName(item.getType().getId(), name)) {
                throw new ConflictException("An item named '" + name + "' already exists for this type");
            }
            item.setName(name);
        }
        if (req.getDescription() != null)
            item.setDescription(req.getDescription());
        if (req.getIconUrl() != null)
            item.setIconUrl(req.getIconUrl());
        if (req.getValue() != null) {
            itemValueValidator.validate(item.getType(), req.getValue());
            item.setValue(ItemMapper.toJsonNode(req.getValue()));
        }
        if (req.getRarity() != null)
            item.setRarity(req.getRarity());
        if (req.getTradeable() != null)
            item.setTradeable(req.getTradeable());
        if (req.getVisible() != null)
            item.setVisible(req.getVisible());
        if (req.getStackable() != null)
            item.setStackable(req.getStackable());
        if (req.getWelcomeGrant() != null)
            item.setWelcomeGrant(req.getWelcomeGrant());
        if (req.getMissionPoolable() != null)
            item.setMissionPoolable(req.getMissionPoolable());
        if (req.getDownloadable() != null)
            item.setDownloadable(req.getDownloadable());
        if (req.getUniquePerUser() != null)
            item.setUniquePerUser(req.getUniquePerUser());
        if (req.getSerialized() != null)
            item.setSerialized(req.getSerialized());
        if (req.getWorth() != null)
            item.setWorth(req.getWorth());
        if (req.getRequirement() != null)
            item.setRequirement(req.getRequirement());
        if (req.getUnlockLevel() != null)
            item.setUnlockLevel(req.getUnlockLevel());
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

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Item uploadImage(UUID id, MultipartFile file) {
        findByIdForStaff(id);
        String url = mediaProcessingService.storeImage(file, ICON_SUBDIR, id.toString());
        return transactionTemplate.execute(status -> setUploadedImage(id, url));
    }

    @Transactional
    public Item removeImage(UUID id) {
        Item item = findByIdForStaff(id);
        mediaProcessingService.deleteIfExists(ICON_SUBDIR, id.toString());
        item.setIconUrl(null);
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

    @Transactional
    public Item deprecate(UUID itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item", itemId));
        if (item.isDeprecated())
            return item;
        item.setDeprecated(true);
        itemRepository.save(item);
        ItemModifier vintage = itemAwardService.loadModifier(ItemModifier.VINTAGE);
        userItemLinkRepository.addModifierToAllLinksOfItem(itemId, vintage.getId());
        return item;
    }
}
