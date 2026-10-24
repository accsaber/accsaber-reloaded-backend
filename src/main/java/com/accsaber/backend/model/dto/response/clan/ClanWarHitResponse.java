package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.MapDifficultyRef;
import com.accsaber.backend.model.dto.response.common.PlayerRef;

public record ClanWarHitResponse(
        UUID id,
        PlayerRef attacker,
        PlayerRef victim,
        int victimCycle,
        MapDifficultyRef map,
        boolean missingScore,
        boolean chip,
        double damage,
        double guardAfter,
        boolean broke,
        double standingMoved,
        Double xpAwarded,
        Instant createdAt) {
}
