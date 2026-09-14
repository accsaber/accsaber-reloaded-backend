package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.clan.war.ClanWarLoan;
import com.accsaber.backend.model.entity.clan.war.ClanWarLoanStatus;

public record ClanWarLoanResponse(
        UUID id,
        ClanWarRefResponse war,
        PlayerRef player,
        PublicClanResponse lendingClan,
        PublicClanResponse clan,
        ClanWarLoanStatus status,
        PlayerRef offeredBy,
        Instant createdAt,
        Instant resolvedAt,
        Instant endedAt) {

    public static ClanWarLoanResponse of(ClanWarLoan loan, Map<UUID, PublicClanResponse> clans) {
        return new ClanWarLoanResponse(loan.getId(), ClanWarRefResponse.of(loan.getWar()), PlayerRef.of(loan.getUser()),
                clans.get(loan.getLendingClan().getId()), clans.get(loan.getClan().getId()), loan.getStatus(),
                PlayerRef.of(loan.getOfferedBy()), loan.getCreatedAt(), loan.getResolvedAt(), loan.getEndedAt());
    }
}
