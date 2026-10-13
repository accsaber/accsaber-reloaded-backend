package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.clan.war.ClanWarAid;
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

    public static ClanWarAidResponse of(ClanWarAid aid, Map<UUID, PublicClanResponse> clans) {
        return new ClanWarAidResponse(aid.getId(), ClanWarRefResponse.of(aid.getWar()),
                clans.get(aid.getClan().getId()), clans.get(aid.getWar().lead(aid.getTeam()).getId()), aid.getTeam(),
                aid.getDirection(), aid.getStatus(), PlayerRef.of(aid.getRequestedBy()), aid.getCreatedAt(),
                aid.getResolvedAt());
    }
}
