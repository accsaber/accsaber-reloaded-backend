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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.item.CreateTradeRequest;
import com.accsaber.backend.model.entity.item.EssenceReason;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.item.TradeItemSide;
import com.accsaber.backend.model.entity.item.TradeStatus;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.model.entity.item.UserItemTrade;
import com.accsaber.backend.model.entity.item.UserItemTradeItem;
import com.accsaber.backend.model.entity.notification.NotificationType;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.item.UserItemLinkRepository;
import com.accsaber.backend.repository.item.UserItemTradeItemRepository;
import com.accsaber.backend.repository.item.UserItemTradeRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.notification.NotificationService;
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
    private final ItemTransferService itemTransferService;
    private final EssenceLedgerService essenceLedgerService;
    private final NotificationService notificationService;

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
            long offeredEssence, long requestedEssence, String message) {
        Long resolvedFrom = duplicateUserService.resolvePrimaryUserId(fromUserId);
        Long resolvedTo = duplicateUserService.resolvePrimaryUserId(toUserId);
        if (resolvedFrom.equals(resolvedTo)) {
            throw new ValidationException("toUserId", "cannot trade with yourself");
        }
        User toUser = userRepository.findById(resolvedTo)
                .orElseThrow(() -> new ResourceNotFoundException("User", toUserId));
        User fromUser = userRepository.findById(resolvedFrom)
                .orElseThrow(() -> new ResourceNotFoundException("User", fromUserId));
        if (offeredEssence < 0 || requestedEssence < 0) {
            throw new ValidationException("essence", "essence amounts cannot be negative");
        }

        Map<UUID, Long> offeredQty = sanitize("offeredItems", offered);
        Map<UUID, Long> requestedQty = sanitize("requestedItems", requested);
        if (offeredQty.isEmpty() && requestedQty.isEmpty() && offeredEssence == 0 && requestedEssence == 0) {
            throw new ValidationException("items", "trade must contain at least one item or some essence");
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

        if (offeredEssence > 0) {
            essenceLedgerService.reserve(resolvedFrom, offeredEssence);
        }

        UserItemTrade trade = UserItemTrade.builder()
                .fromUser(fromUser)
                .toUser(toUser)
                .status(TradeStatus.pending)
                .offeredEssence(offeredEssence)
                .requestedEssence(requestedEssence)
                .message(message)
                .items(new ArrayList<>())
                .build();
        offeredQty.forEach((linkId, qty) -> trade.getItems()
                .add(buildItem(trade, offeredLinks.get(linkId), TradeItemSide.offered, qty)));
        requestedQty.forEach((linkId, qty) -> trade.getItems()
                .add(buildItem(trade, requestedLinks.get(linkId), TradeItemSide.requested, qty)));
        UserItemTrade saved = tradeRepository.save(trade);

        notificationService.notify(resolvedTo, NotificationType.trade_offer, resolvedFrom,
                "You received a new trade offer", "/trade-offers");
        return saved;
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

        if (trade.getRequestedEssence() > 0) {
            essenceLedgerService.debit(receiverId, trade.getRequestedEssence(),
                    EssenceReason.trade_payment, trade.getId());
            essenceLedgerService.credit(senderId, trade.getRequestedEssence(),
                    EssenceReason.trade_receipt, trade.getId());
        }
        if (trade.getOfferedEssence() > 0) {
            essenceLedgerService.settleReserved(senderId, receiverId, trade.getOfferedEssence(),
                    EssenceReason.trade_payment, EssenceReason.trade_receipt, trade.getId());
        }

        for (UserItemTradeItem entry : trade.getItems()) {
            UserItemLink source = entry.getUserItemLink();
            Long newOwner = entry.getSide() == TradeItemSide.offered ? receiverId : senderId;
            itemTransferService.transfer(source, newOwner, entry.getQuantity(),
                    ItemSource.trade, "Received via trade");
        }

        trade.setStatus(TradeStatus.accepted);
        trade.setResolvedAt(Instant.now());
        notifyResolution(trade, NotificationType.trade_accepted, receiverId);
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
        refundOfferedEssence(trade);
        trade.setStatus(TradeStatus.declined);
        trade.setResolvedAt(Instant.now());
        notifyResolution(trade, NotificationType.trade_declined, resolved);
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
        refundOfferedEssence(trade);
        trade.setStatus(TradeStatus.cancelled);
        trade.setResolvedAt(Instant.now());
        return tradeRepository.save(trade);
    }

    @Transactional
    public int expireOlderThan(Instant cutoff) {
        for (UserItemTrade trade : tradeRepository.findExpiringWithReservedEssence(cutoff)) {
            refundOfferedEssence(trade);
        }
        return tradeRepository.expirePending(cutoff, Instant.now());
    }

    private void notifyResolution(UserItemTrade trade, NotificationType type, Long actorId) {
        String title = type == NotificationType.trade_accepted
                ? "Your trade offer was accepted"
                : "Your trade offer was declined";
        notificationService.notify(trade.getFromUser().getId(), type, actorId, title, "/trade-offers");
    }

    private void refundOfferedEssence(UserItemTrade trade) {
        if (trade.getOfferedEssence() > 0) {
            essenceLedgerService.release(trade.getFromUser().getId(), trade.getOfferedEssence());
        }
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
            if (link.isEscrowed()) {
                throw new ValidationException(sideLabel,
                        "item '" + link.getItem().getName() + "' is listed on the market and cannot be traded");
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
