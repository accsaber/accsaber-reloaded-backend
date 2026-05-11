package com.accsaber.backend.service.item;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import com.accsaber.backend.model.dto.request.item.CreateTradeRequest;
import com.accsaber.backend.model.entity.item.ItemModifier;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.item.TradeItemSide;
import com.accsaber.backend.model.entity.item.TradeStatus;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.model.entity.item.UserItemTrade;
import com.accsaber.backend.model.entity.item.UserItemTradeItem;
import com.accsaber.backend.repository.item.UserItemLinkRepository;
import com.accsaber.backend.repository.item.UserItemTradeItemRepository;
import com.accsaber.backend.repository.item.UserItemTradeRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemTradeService {

    private static final int MAX_ITEMS_PER_SIDE = 8;

    private final UserItemTradeRepository tradeRepository;
    private final UserItemTradeItemRepository tradeItemRepository;
    private final UserItemLinkRepository userItemLinkRepository;
    private final UserRepository userRepository;
    private final DuplicateUserService duplicateUserService;
    private final ItemService itemService;

    public List<UserItemTrade> listIncomingPending(Long toUserId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(toUserId);
        return tradeRepository.findByToUser_IdAndStatus(resolved, TradeStatus.pending);
    }

    public List<UserItemTrade> listOutgoingPending(Long fromUserId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(fromUserId);
        return tradeRepository.findByFromUser_IdAndStatus(resolved, TradeStatus.pending);
    }

    public Page<UserItemTrade> listForUser(Long userId, String direction, Collection<TradeStatus> statuses,
            Pageable pageable) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        boolean incoming = direction == null || direction.equalsIgnoreCase("incoming")
                || direction.equalsIgnoreCase("both");
        boolean outgoing = direction == null || direction.equalsIgnoreCase("outgoing")
                || direction.equalsIgnoreCase("both");
        Collection<TradeStatus> effective = (statuses == null || statuses.isEmpty())
                ? Arrays.asList(TradeStatus.values())
                : statuses;
        return tradeRepository.findByDirectionAndStatusIn(resolved, incoming, outgoing, effective, pageable);
    }

    public UserItemTrade findById(UUID id) {
        return tradeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UserItemTrade", id));
    }

    @Transactional
    public UserItemTrade create(Long fromUserId, Long toUserId,
            List<CreateTradeRequest.TradeItem> offered, List<CreateTradeRequest.TradeItem> requested,
            String message) {
        Long resolvedFrom = duplicateUserService.resolvePrimaryUserId(fromUserId);
        Long resolvedTo = duplicateUserService.resolvePrimaryUserId(toUserId);
        if (resolvedFrom.equals(resolvedTo)) {
            throw new ValidationException("toUserId", "cannot trade with yourself");
        }
        if (!userRepository.existsById(resolvedTo)) {
            throw new ResourceNotFoundException("User", toUserId);
        }

        Map<UUID, Long> offeredQty = sanitize("offeredItems", offered);
        Map<UUID, Long> requestedQty = sanitize("requestedItems", requested);
        if (offeredQty.isEmpty() && requestedQty.isEmpty()) {
            throw new ValidationException("items", "trade must contain at least one item");
        }
        Set<UUID> intersection = new HashSet<>(offeredQty.keySet());
        intersection.retainAll(requestedQty.keySet());
        if (!intersection.isEmpty()) {
            throw new ValidationException("items", "the same item cannot appear on both sides");
        }

        Map<UUID, UserItemLink> offeredLinks = loadAndValidate(offeredQty, resolvedFrom, "offered");
        Map<UUID, UserItemLink> requestedLinks = loadAndValidate(requestedQty, resolvedTo, "requested");

        Set<UUID> all = new HashSet<>();
        all.addAll(offeredQty.keySet());
        all.addAll(requestedQty.keySet());
        List<UUID> conflicts = tradeItemRepository.findLinkIdsInTradesWithStatus(all, TradeStatus.pending);
        if (!conflicts.isEmpty()) {
            throw new ValidationException("items",
                    "one or more items are already in another pending trade: " + conflicts);
        }

        UserItemTrade trade = UserItemTrade.builder()
                .fromUser(userRepository.getReferenceById(resolvedFrom))
                .toUser(userRepository.getReferenceById(resolvedTo))
                .status(TradeStatus.pending)
                .message(message)
                .items(new ArrayList<>())
                .build();
        offeredQty.forEach((linkId, qty) -> trade.getItems()
                .add(buildItem(trade, offeredLinks.get(linkId), TradeItemSide.offered, qty)));
        requestedQty.forEach((linkId, qty) -> trade.getItems()
                .add(buildItem(trade, requestedLinks.get(linkId), TradeItemSide.requested, qty)));
        return tradeRepository.save(trade);
    }

    @Transactional
    public UserItemTrade accept(UUID tradeId, Long actingUserId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(actingUserId);
        UserItemTrade trade = findById(tradeId);
        ensurePending(trade);
        if (!trade.getToUser().getId().equals(resolved)) {
            throw new ValidationException("tradeId", "only the recipient can accept this trade");
        }

        Long senderId = trade.getFromUser().getId();
        Long receiverId = trade.getToUser().getId();

        for (UserItemTradeItem entry : trade.getItems()) {
            UserItemLink link = entry.getUserItemLink();
            if (!link.getItem().isTradeable() || link.getItem().isDeprecated()) {
                throw new ValidationException("tradeId",
                        "item '" + link.getItem().getName() + "' is no longer tradeable");
            }
            Long expectedOwner = entry.getSide() == TradeItemSide.offered ? senderId : receiverId;
            if (!link.getUser().getId().equals(expectedOwner)) {
                throw new ValidationException("tradeId",
                        "ownership of item '" + link.getItem().getName() + "' has changed since the trade was created");
            }
            if (entry.getQuantity() > link.getQuantity()) {
                throw new ValidationException("tradeId",
                        "stack size for '" + link.getItem().getName() + "' has dropped below the trade quantity");
            }
        }

        List<UnequipCandidate> unequipCandidates = new ArrayList<>();
        for (UserItemTradeItem entry : trade.getItems()) {
            UserItemLink source = entry.getUserItemLink();
            Long previousOwner = source.getUser().getId();
            Long newOwner = entry.getSide() == TradeItemSide.offered ? receiverId : senderId;
            boolean linkGone = transfer(source, newOwner, entry.getQuantity());
            if (linkGone) {
                unequipCandidates.add(new UnequipCandidate(previousOwner, source.getId(),
                        source.getItem().getType().getKey()));
            }
        }
        userItemLinkRepository.flush();
        for (UnequipCandidate c : unequipCandidates) {
            itemService.clearEquippedIfLinkGone(c.userId(), c.linkId(), c.typeKey());
        }

        trade.setStatus(TradeStatus.accepted);
        trade.setResolvedAt(Instant.now());
        return tradeRepository.save(trade);
    }

    @Transactional
    public UserItemTrade decline(UUID tradeId, Long actingUserId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(actingUserId);
        UserItemTrade trade = findById(tradeId);
        ensurePending(trade);
        if (!trade.getToUser().getId().equals(resolved)) {
            throw new ValidationException("tradeId", "only the recipient can decline this trade");
        }
        trade.setStatus(TradeStatus.declined);
        trade.setResolvedAt(Instant.now());
        return tradeRepository.save(trade);
    }

    @Transactional
    public UserItemTrade cancel(UUID tradeId, Long actingUserId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(actingUserId);
        UserItemTrade trade = findById(tradeId);
        ensurePending(trade);
        if (!trade.getFromUser().getId().equals(resolved)) {
            throw new ValidationException("tradeId", "only the sender can cancel this trade");
        }
        trade.setStatus(TradeStatus.cancelled);
        trade.setResolvedAt(Instant.now());
        return tradeRepository.save(trade);
    }

    @Transactional
    public int expireOlderThan(Instant cutoff) {
        return tradeRepository.expirePending(cutoff, Instant.now());
    }

    private boolean transfer(UserItemLink source, Long newOwnerId, long qty) {
        if (ItemService.isInstanced(source)) {
            source.setUser(userRepository.getReferenceById(newOwnerId));
            userItemLinkRepository.save(source);
            return false;
        }
        UserItemLink existing = findIdenticalStack(newOwnerId, source.getItem().getId(), source.getModifiers());
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + qty);
            userItemLinkRepository.save(existing);
        } else {
            UserItemLink fresh = UserItemLink.builder()
                    .user(userRepository.getReferenceById(newOwnerId))
                    .item(source.getItem())
                    .modifiers(new HashSet<>(source.getModifiers()))
                    .serialNumber(null)
                    .quantity(qty)
                    .source(ItemSource.trade)
                    .reason("Received via trade")
                    .build();
            userItemLinkRepository.save(fresh);
        }
        long remaining = source.getQuantity() - qty;
        if (remaining <= 0) {
            userItemLinkRepository.delete(source);
            return true;
        }
        source.setQuantity(remaining);
        userItemLinkRepository.save(source);
        return false;
    }

    private record UnequipCandidate(Long userId, UUID linkId, String typeKey) {
    }

    private UserItemLink findIdenticalStack(Long userId, UUID itemId, Set<ItemModifier> modifiers) {
        Set<UUID> targetIds = modifiers.stream().map(ItemModifier::getId).collect(Collectors.toSet());
        return userItemLinkRepository.findByUser_IdAndItem_Id(userId, itemId).stream()
                .filter(l -> ItemService.sameModifierSet(l.getModifiers(), targetIds))
                .findFirst()
                .orElse(null);
    }

    private void ensurePending(UserItemTrade trade) {
        if (trade.getStatus() != TradeStatus.pending) {
            throw new ValidationException("tradeId", "trade is not pending (status: " + trade.getStatus() + ")");
        }
    }

    private Map<UUID, Long> sanitize(String fieldName, List<CreateTradeRequest.TradeItem> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        if (raw.size() > MAX_ITEMS_PER_SIDE) {
            throw new ValidationException(fieldName,
                    "at most " + MAX_ITEMS_PER_SIDE + " items per side");
        }
        Map<UUID, Long> result = new LinkedHashMap<>();
        for (CreateTradeRequest.TradeItem entry : raw) {
            if (entry == null || entry.getUserItemLinkId() == null) {
                throw new ValidationException(fieldName, "missing userItemLinkId");
            }
            if (entry.getQuantity() < 1) {
                throw new ValidationException(fieldName, "quantity must be at least 1");
            }
            if (result.put(entry.getUserItemLinkId(), entry.getQuantity()) != null) {
                throw new ValidationException(fieldName, "duplicate userItemLinkId");
            }
        }
        return result;
    }

    private Map<UUID, UserItemLink> loadAndValidate(Map<UUID, Long> entries, Long ownerId, String sideLabel) {
        if (entries.isEmpty()) {
            return Map.of();
        }
        List<UserItemLink> links = userItemLinkRepository.findAllById(entries.keySet());
        Map<UUID, UserItemLink> byId = new HashMap<>();
        for (UserItemLink l : links) {
            byId.put(l.getId(), l);
        }
        for (Map.Entry<UUID, Long> e : entries.entrySet()) {
            UUID id = e.getKey();
            long requestedQty = e.getValue();
            UserItemLink link = byId.get(id);
            if (link == null || !link.getUser().getId().equals(ownerId)) {
                throw new ValidationException(sideLabel,
                        sideLabel + " item " + id + " is not owned by the expected user");
            }
            if (!link.getItem().isTradeable()) {
                throw new ValidationException(sideLabel,
                        "item '" + link.getItem().getName() + "' is not tradeable");
            }
            if (link.getItem().isDeprecated()) {
                throw new ValidationException(sideLabel,
                        "item '" + link.getItem().getName() + "' is deprecated and cannot be traded");
            }
            if (!link.getItem().isStackable() && requestedQty != 1) {
                throw new ValidationException(sideLabel,
                        "item '" + link.getItem().getName() + "' is non-stackable; quantity must be 1");
            }
            if (requestedQty > link.getQuantity()) {
                throw new ValidationException(sideLabel,
                        "trade quantity exceeds owned quantity for '" + link.getItem().getName() + "'");
            }
        }
        return byId;
    }

    private UserItemTradeItem buildItem(UserItemTrade trade, UserItemLink link, TradeItemSide side, long quantity) {
        return UserItemTradeItem.builder()
                .trade(trade)
                .userItemLink(link)
                .side(side)
                .quantity(quantity)
                .build();
    }
}
