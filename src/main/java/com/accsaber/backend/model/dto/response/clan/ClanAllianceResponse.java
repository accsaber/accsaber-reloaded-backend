package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.dto.response.item.ItemResponse;
import com.accsaber.backend.model.entity.clan.ClanAlliance;
import com.accsaber.backend.model.entity.clan.ClanAllianceStatus;

public record ClanAllianceResponse(
        UUID id,
        PublicClanResponse ally,
        ClanAllianceStatus status,
        boolean incoming,
        PlayerRef proposedBy,
        PlayerRef endedBy,
        ClanTrustResponse trust,
        Instant createdAt,
        Instant acceptedAt,
        Instant endedAt) {

    public static ClanAllianceResponse of(ClanAlliance alliance, UUID clanId, List<ItemResponse> allyEquipped,
            ClanTrustResponse trust) {
        return new ClanAllianceResponse(alliance.getId(),
                PublicClanResponse.of(alliance.otherThan(clanId), allyEquipped), alliance.getStatus(),
                !alliance.getProposedByClan().getId().equals(clanId), PlayerRef.of(alliance.getProposedByUser()),
                PlayerRef.of(alliance.getEndedByUser()), trust, alliance.getCreatedAt(), alliance.getAcceptedAt(),
                alliance.getEndedAt());
    }
}
