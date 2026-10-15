package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.clan.ClanAllianceStatus;

public record ClanAllianceResponse(
        UUID id,
        PublicClanResponse ally,
        ClanAllianceStatus status,
        boolean incoming,
        PlayerRef proposedBy,
        PlayerRef endedBy,
        long warsFoughtTogether,
        Instant createdAt,
        Instant acceptedAt,
        Instant endedAt) {
}
