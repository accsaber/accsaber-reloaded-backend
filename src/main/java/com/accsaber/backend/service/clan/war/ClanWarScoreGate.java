package com.accsaber.backend.service.clan.war;

import java.util.Set;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.accsaber.backend.repository.clan.war.ClanWarRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClanWarScoreGate {

    private final ClanWarRepository warRepository;

    private volatile Set<UUID> poolDifficultyIds = Set.of();
    private volatile Set<Long> participantIds = Set.of();

    @PostConstruct
    public void init() {
        refresh();
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    public void refresh() {
        poolDifficultyIds = Set.copyOf(warRepository.findActivePoolDifficultyIds());
        participantIds = Set.copyOf(warRepository.findActiveParticipantIds());
    }

    public void refreshAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            refresh();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                refresh();
            }
        });
    }

    public boolean mayMatter(Long userId, UUID mapDifficultyId) {
        return participantIds.contains(userId) && poolDifficultyIds.contains(mapDifficultyId);
    }
}
