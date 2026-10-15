package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.clan.ClanJoinDirection;
import com.accsaber.backend.model.entity.clan.ClanJoinStatus;

public record ClanJoinRequestResponse(
        UUID id,
        PublicClanResponse clan,
        PlayerRef player,
        ClanJoinDirection direction,
        ClanJoinStatus status,
        PlayerRef createdBy,
        PlayerRef resolvedBy,
        Instant createdAt,
        Instant resolvedAt) {
}
