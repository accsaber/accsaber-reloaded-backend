package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.PlayerRef;

public record ClanWarParticipantResponse(
        PlayerRef player,
        UUID clanId,
        PlayerRef target,
        ClanWarOrdersResponse orders,
        double skill,
        ClanWarStrikePreviewResponse strikePreview,
        double standingWeight,
        double guard,
        int guardCycle,
        int breaksSuffered,
        Instant brokenAt,
        double contribution,
        Instant joinedAt,
        Instant leftAt) {
}
