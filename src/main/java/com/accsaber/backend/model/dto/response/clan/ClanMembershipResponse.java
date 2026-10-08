package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;

import com.accsaber.backend.model.entity.clan.ClanRole;

public record ClanMembershipResponse(
        ClanRole role,
        Instant joinedAt,
        boolean online,
        Instant lastPlayedAt,
        double strengthShare,
        double seasonPlayXp,
        long seasonHits,
        long seasonBreaks) {
}
