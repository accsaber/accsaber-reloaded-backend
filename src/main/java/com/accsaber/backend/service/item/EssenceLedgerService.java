package com.accsaber.backend.service.item;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.item.EssenceReason;
import com.accsaber.backend.model.entity.item.EssenceTransaction;
import com.accsaber.backend.repository.item.EssenceTransactionRepository;
import com.accsaber.backend.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class EssenceLedgerService {

    private final UserRepository userRepository;
    private final EssenceTransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public long balance(Long resolvedUserId) {
        return userRepository.findItemEssenceById(resolvedUserId).orElse(0L);
    }

    @Transactional(readOnly = true)
    public long reserved(Long resolvedUserId) {
        return userRepository.findReservedEssenceById(resolvedUserId).orElse(0L);
    }

    public void credit(Long resolvedUserId, long amount, EssenceReason reason, UUID refId) {
        requirePositive(amount);
        userRepository.addItemEssence(resolvedUserId, amount);
        record(resolvedUserId, amount, reason, refId);
    }

    public void debit(Long resolvedUserId, long amount, EssenceReason reason, UUID refId) {
        requirePositive(amount);
        if (userRepository.debitEssence(resolvedUserId, amount) == 0) {
            throw new ValidationException("essence", "insufficient essence balance");
        }
        record(resolvedUserId, -amount, reason, refId);
    }

    public void reserve(Long resolvedUserId, long amount) {
        requirePositive(amount);
        if (userRepository.reserveEssence(resolvedUserId, amount) == 0) {
            throw new ValidationException("essence", "insufficient essence balance");
        }
    }

    public void release(Long resolvedUserId, long amount) {
        requirePositive(amount);
        if (userRepository.releaseEssence(resolvedUserId, amount) == 0) {
            throw new ConflictException("Reserved essence is lower than the amount being released");
        }
    }

    public void settleReserved(Long buyerId, Long sellerId, long amount, EssenceReason buyerReason,
            EssenceReason sellerReason, UUID refId) {
        requirePositive(amount);
        if (userRepository.consumeReservedEssence(buyerId, amount) == 0) {
            throw new ConflictException("Reserved essence is lower than the settlement amount");
        }
        record(buyerId, -amount, buyerReason, refId);
        userRepository.addItemEssence(sellerId, amount);
        record(sellerId, amount, sellerReason, refId);
    }

    private void record(Long resolvedUserId, long signedAmount, EssenceReason reason, UUID refId) {
        transactionRepository.save(EssenceTransaction.builder()
                .user(userRepository.getReferenceById(resolvedUserId))
                .amount(signedAmount)
                .reason(reason)
                .refId(refId)
                .build());
    }

    private void requirePositive(long amount) {
        if (amount <= 0) {
            throw new ValidationException("amount", "essence amount must be a positive whole number");
        }
    }
}
