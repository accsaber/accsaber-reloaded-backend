package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;

import com.accsaber.backend.model.dto.response.common.PlayerRef;

public record ClanWarParticipantResponse(
        PlayerRef player,
        PublicClanResponse clan,
        PlayerRef target,
        double skill,
        ClanWarStrikePreviewResponse strikePreview,
        double standingWeight,
        double guard,
        int guardCycle,
        int breaksSuffered,
        double contribution,
        Instant joinedAt,
        Instant leftAt) {
}
