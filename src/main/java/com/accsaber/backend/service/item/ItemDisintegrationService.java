package com.accsaber.backend.service.item;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.item.DisintegrateRequest;
import com.accsaber.backend.model.dto.response.item.DisintegrationResponse;
import com.accsaber.backend.model.dto.response.item.EssenceBalanceResponse;
import com.accsaber.backend.model.entity.item.EssenceReason;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.TradeStatus;
import com.accsaber.backend.model.entity.item.UserItemDisintegration;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.model.event.InventoryChangedEvent;
import com.accsaber.backend.repository.item.UserItemDisintegrationRepository;
import com.accsaber.backend.repository.item.UserItemLinkRepository;
import com.accsaber.backend.repository.item.UserItemTradeItemRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemDisintegrationService {

    private final UserItemLinkRepository userItemLinkRepository;
    private final UserRepository userRepository;
    private final DuplicateUserService duplicateUserService;
    private final UserItemTradeItemRepository tradeItemRepository;
    private final UserItemDisintegrationRepository disintegrationRepository;
    private final EssenceLedgerService essenceLedgerService;
    private final ItemEquipService itemEquipService;
    private final ApplicationEventPublisher eventPublisher;

    public EssenceBalanceResponse getEssence(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        return EssenceBalanceResponse.builder()
                .balance(essenceLedgerService.balance(resolved))
                .reserved(essenceLedgerService.reserved(resolved))
                .build();
    }

    @Transactional
    public DisintegrationResponse disintegrate(Long userId, List<DisintegrateRequest.Entry> requested) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        Map<UUID, Long> requestedByLink = requestedQuantities(requested);
        List<UserItemLink> links = lockForDisintegration(resolved, requestedByLink);
        Set<UUID> equipped = itemEquipService.equippedLinkIds(resolved);
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
}
