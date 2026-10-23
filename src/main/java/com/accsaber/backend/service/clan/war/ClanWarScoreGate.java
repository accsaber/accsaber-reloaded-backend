package com.accsaber.backend.service.clan.war;

import java.util.Set;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.repository.clan.war.ClanWarRepository;
import com.accsaber.backend.service.infra.AfterCommit;
import com.accsaber.backend.service.infra.ModifierCacheService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClanWarScoreGate {

    private final ClanWarRepository warRepository;
    private final ModifierCacheService modifierCacheService;

    private volatile Set<UUID> poolDifficultyIds = Set.of();
    private volatile Set<Long> participantIds = Set.of();

    @PostConstruct
    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    public void refresh() {
        poolDifficultyIds = Set.copyOf(warRepository.findActivePoolDifficultyIds());
        participantIds = Set.copyOf(warRepository.findActiveParticipantIds());
    }

    public void refreshAfterCommit() {
        AfterCommit.run(this::refresh);
    }

    public boolean mayMatter(ScoreResponse score) {
        return participantIds.contains(Long.valueOf(score.getUserId()))
                && poolDifficultyIds.contains(score.getMapDifficultyId())
                && !modifierCacheService.containsSpeedUp(score.getModifierIds());
    }
}
