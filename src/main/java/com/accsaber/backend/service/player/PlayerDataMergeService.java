package com.accsaber.backend.service.player;

import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.model.entity.item.EssenceReason;
import com.accsaber.backend.model.event.PlayersMergedEvent;
import com.accsaber.backend.service.item.EssenceLedgerService;
import com.accsaber.backend.service.item.ItemTransferService;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlayerDataMergeService {

    private final ItemTransferService itemTransferService;
    private final EssenceLedgerService essenceLedgerService;
    private final EntityManager entityManager;

    @EventListener
    @Transactional
    public void onPlayersMerged(PlayersMergedEvent event) {
        Long secondaryId = event.secondaryUserId();
        boolean locked = (boolean) entityManager.createNativeQuery(PlayerDataMergeQueries.HAS_LOCKED_HOLDINGS)
                .setParameter("secondary", secondaryId)
                .getSingleResult();
        if (locked) {
            throw new ConflictException("Player " + secondaryId
                    + " has items or essence locked in a trade or market listing. Merge once those have finished");
        }
        itemTransferService.moveAll(secondaryId, event.primaryUserId());
        moveEssence(secondaryId, event.primaryUserId());
        for (String move : PlayerDataMergeQueries.MOVES) {
            entityManager.createNativeQuery(move)
                    .setParameter("secondary", secondaryId)
                    .setParameter("primary", event.primaryUserId())
                    .executeUpdate();
        }
    }

    private void moveEssence(Long secondaryId, Long primaryId) {
        long balance = essenceLedgerService.balance(secondaryId);
        if (balance == 0) {
            return;
        }
        UUID refId = UUID.randomUUID();
        essenceLedgerService.debit(secondaryId, balance, EssenceReason.admin_adjustment, refId);
        essenceLedgerService.credit(primaryId, balance, EssenceReason.admin_adjustment, refId);
    }
}
