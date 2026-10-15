package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.dto.response.map.PublicMapDifficultyResponse;

public record ClanWarHitResponse(
        UUID id,
        PlayerRef attacker,
        PlayerRef victim,
        int victimCycle,
        PublicMapDifficultyResponse difficulty,
        boolean missingScore,
        boolean chip,
        double damage,
        double guardAfter,
        boolean broke,
        double standingMoved,
        Double xpAwarded,
        Instant createdAt) {
}
