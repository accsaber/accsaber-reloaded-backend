package com.accsaber.backend.model.dto.response.clan;

import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.entity.clan.war.ClanWarTeam;

public record ClanWarTeamResponse(
        PublicClanResponse lead,
        double stake,
        double stakeRemaining,
        List<ClanWarSideResponse> sides) {

    public static ClanWarTeamResponse of(UUID leadId, ClanWarTeam team, List<ClanWarSideResponse> all) {
        List<ClanWarSideResponse> sides = all.stream().filter(side -> side.team() == team).toList();
        PublicClanResponse lead = sides.stream().map(ClanWarSideResponse::clan)
                .filter(clan -> clan.id().equals(leadId)).findFirst().orElse(null);
        return new ClanWarTeamResponse(lead, sides.stream().mapToDouble(ClanWarSideResponse::stake).sum(),
                sides.stream().filter(side -> side.leftAt() == null).mapToDouble(ClanWarSideResponse::stakeRemaining)
                        .sum(),
                sides);
    }
}
