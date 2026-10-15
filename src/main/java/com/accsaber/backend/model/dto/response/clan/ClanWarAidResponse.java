package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.clan.war.ClanWarAidDirection;
import com.accsaber.backend.model.entity.clan.war.ClanWarAidStatus;
import com.accsaber.backend.model.entity.clan.war.ClanWarTeam;

public record ClanWarAidResponse(
        UUID id,
        ClanWarRefResponse war,
        PublicClanResponse clan,
        PublicClanResponse lead,
        ClanWarTeam team,
        ClanWarAidDirection direction,
        ClanWarAidStatus status,
        PlayerRef requestedBy,
        Instant createdAt,
        Instant resolvedAt) {
}
