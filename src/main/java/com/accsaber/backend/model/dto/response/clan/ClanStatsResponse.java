package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;

import com.accsaber.backend.model.entity.clan.ClanRole;

public record ClanStatsResponse(
        PublicClanResponse clan,
        ClanRole role,
        Instant joinedAt,
        long warsFought,
        long warsWon,
        long hits,
        long breaksDealt,
        long breaksSuffered,
        double standingMoved,
        double contribution,
        double warXp) {
}
