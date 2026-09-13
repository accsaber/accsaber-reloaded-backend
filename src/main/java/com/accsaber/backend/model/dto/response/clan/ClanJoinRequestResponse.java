package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.dto.response.item.ItemResponse;
import com.accsaber.backend.model.entity.clan.ClanJoinDirection;
import com.accsaber.backend.model.entity.clan.ClanJoinRequest;
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

    public static ClanJoinRequestResponse of(ClanJoinRequest request, List<ItemResponse> equipped) {
        return new ClanJoinRequestResponse(request.getId(), PublicClanResponse.of(request.getClan(), equipped),
                PlayerRef.of(request.getUser()), request.getDirection(), request.getStatus(),
                PlayerRef.of(request.getCreatedBy()), PlayerRef.of(request.getResolvedBy()),
                request.getCreatedAt(), request.getResolvedAt());
    }
}
