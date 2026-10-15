package com.accsaber.backend.service.item;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.item.ItemModifier;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.item.TradeStatus;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.repository.item.UserItemLinkRepository;
import com.accsaber.backend.repository.item.UserItemTradeItemRepository;
import com.accsaber.backend.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ItemTransferService {

    private final UserItemLinkRepository userItemLinkRepository;
    private final UserRepository userRepository;
    private final UserItemTradeItemRepository tradeItemRepository;
    private final ItemEquipService itemEquipService;

    public void transfer(UserItemLink source, Long newOwnerId, long qty, ItemSource sourceKind, String reason) {
        Long previousOwner = source.getUser().getId();
        String typeKey = source.getItem().getType().getKey();

        if (ItemAwardService.isInstanced(source)) {
            requireWholeLink(source, qty);
            source.setUser(userRepository.getReferenceById(newOwnerId));
            source.setEscrowed(false);
            ItemAwardService.reattribute(source, sourceKind, null, null, reason);
            userItemLinkRepository.save(source);
            itemEquipService.clearEquippedIfLinkGone(previousOwner, source.getId(), typeKey);
            return;
        }

        UserItemLink existing = findIdenticalStack(newOwnerId, source.getItem().getId(), source.getModifiers());
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + qty);
            ItemAwardService.reattribute(existing, sourceKind, null, null, reason);
            userItemLinkRepository.save(existing);
        } else {
            userItemLinkRepository.save(UserItemLink.builder()
                    .user(userRepository.getReferenceById(newOwnerId))
                    .item(source.getItem())
                    .modifiers(new HashSet<>(source.getModifiers()))
                    .unusualEffect(source.getUnusualEffect())
                    .serialNumber(null)
                    .quantity(qty)
                    .escrowed(false)
                    .source(sourceKind)
                    .reason(reason)
                    .build());
        }

        long remaining = source.getQuantity() - qty;
        if (remaining > 0) {
            source.setQuantity(remaining);
            userItemLinkRepository.save(source);
            return;
        }
        userItemLinkRepository.delete(source);
        userItemLinkRepository.flush();
        itemEquipService.clearEquippedIfLinkGone(previousOwner, source.getId(), typeKey);
    }

    public UserItemLink escrow(UserItemLink source, long qty) {
        if (qty < 1 || qty > source.getQuantity()) {
            throw new ValidationException("quantity", "quantity exceeds the amount you own");
        }
        if (ItemAwardService.isInstanced(source)) {
            requireWholeLink(source, qty);
        }
        if (qty == source.getQuantity()) {
            source.setEscrowed(true);
            return userItemLinkRepository.save(source);
        }

        source.setQuantity(source.getQuantity() - qty);
        userItemLinkRepository.save(source);
        return userItemLinkRepository.save(UserItemLink.builder()
                .user(source.getUser())
                .item(source.getItem())
                .modifiers(new HashSet<>(source.getModifiers()))
                .unusualEffect(source.getUnusualEffect())
                .serialNumber(null)
                .quantity(qty)
                .escrowed(true)
                .source(source.getSource())
                .sourceId(source.getSourceId())
                .awardedBy(source.getAwardedBy())
                .awardedAt(source.getAwardedAt())
                .reason(source.getReason())
                .build());
    }

    public void releaseEscrow(UserItemLink escrowLink) {
        escrowLink.setEscrowed(false);
        absorbIdenticalStack(escrowLink, escrowLink.getUser().getId());
        userItemLinkRepository.save(escrowLink);
    }

    public void transferEscrowed(UserItemLink escrowLink, Long newOwnerId, ItemSource sourceKind, String reason) {
        Long previousOwner = escrowLink.getUser().getId();
        String typeKey = escrowLink.getItem().getType().getKey();

        escrowLink.setUser(userRepository.getReferenceById(newOwnerId));
        escrowLink.setEscrowed(false);
        ItemAwardService.reattribute(escrowLink, sourceKind, null, null, reason);
        absorbIdenticalStack(escrowLink, newOwnerId);
        userItemLinkRepository.save(escrowLink);
        itemEquipService.clearEquippedIfLinkGone(previousOwner, escrowLink.getId(), typeKey);
    }

    private void absorbIdenticalStack(UserItemLink target, Long ownerId) {
        if (ItemAwardService.isInstanced(target)) {
            return;
        }
        UserItemLink existing = findIdenticalStack(ownerId, target.getItem().getId(), target.getModifiers());
        if (existing == null || existing.getId().equals(target.getId()) || !tradeItemRepository
                .findLinkIdsInTradesWithStatus(List.of(existing.getId()), TradeStatus.pending).isEmpty()) {
            return;
        }
        target.setQuantity(target.getQuantity() + existing.getQuantity());
        userItemLinkRepository.delete(existing);
        userItemLinkRepository.flush();
        itemEquipService.clearEquippedIfLinkGone(ownerId, existing.getId(), target.getItem().getType().getKey());
    }

    private void requireWholeLink(UserItemLink source, long qty) {
        if (qty != source.getQuantity()) {
            throw new ValidationException("quantity",
                    "'" + source.getItem().getName() + "' is a unique instance and cannot be split");
        }
    }

    private UserItemLink findIdenticalStack(Long userId, UUID itemId, Set<ItemModifier> modifiers) {
        Set<UUID> targetIds = modifiers.stream().map(ItemModifier::getId).collect(Collectors.toSet());
        return userItemLinkRepository.findStacksForUpdate(userId, itemId).stream()
                .filter(l -> ItemAwardService.sameModifierSet(l.getModifiers(), targetIds))
                .findFirst()
                .orElse(null);
    }
}
