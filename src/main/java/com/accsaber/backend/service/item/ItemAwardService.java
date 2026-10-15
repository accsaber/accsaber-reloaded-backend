package com.accsaber.backend.service.item;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemModifier;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.item.UnusualEffect;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.model.entity.notification.NotificationType;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.repository.item.ItemModifierRepository;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.item.UnusualEffectRepository;
import com.accsaber.backend.repository.item.UserItemLinkRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.notification.NotificationService;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemAwardService {

    private final ItemRepository itemRepository;
    private final UserItemLinkRepository userItemLinkRepository;
    private final UserRepository userRepository;
    private final DuplicateUserService duplicateUserService;
    private final ItemModifierRepository itemModifierRepository;
    private final UnusualEffectRepository unusualEffectRepository;
    private final ModifierResolver modifierResolver;
    private final NotificationService notificationService;
    private final ItemEquipService itemEquipService;
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void revokeAward(UUID linkId) {
        UserItemLink link = userItemLinkRepository.findById(linkId)
                .orElseThrow(() -> new ResourceNotFoundException("UserItemLink", linkId));
        Long ownerId = link.getUser().getId();
        String typeKey = link.getItem().getType().getKey();
        userItemLinkRepository.delete(link);
        userItemLinkRepository.flush();
        itemEquipService.clearEquippedIfLinkGone(ownerId, linkId, typeKey);
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

    static boolean hasModifier(Set<ItemModifier> modifiers, String key) {
        return modifiers != null && modifiers.stream().anyMatch(m -> key.equals(m.getKey()));
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
        return userItemLinkRepository.findStacksForUpdate(userId, itemId).stream()
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

    Set<ItemModifier> loadModifierSet(Collection<String> keys) {
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

    ItemModifier loadModifier(String key) {
        return itemModifierRepository.findByKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("ItemModifier", key));
    }
}
