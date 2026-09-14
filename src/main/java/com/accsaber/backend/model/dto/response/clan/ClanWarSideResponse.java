package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.clan.war.ClanWarSide;

public record ClanWarSideResponse(
        PublicClanResponse clan,
        PlayerRef lead,
        double stake,
        double stakeRemaining,
        double standingAtDeclare,
        Instant picksSubmittedAt) {

    public static ClanWarSideResponse of(ClanWarSide side, PublicClanResponse clan) {
        return new ClanWarSideResponse(clan, PlayerRef.of(side.getLeadUser()), side.getStake(),
                side.getStakeRemaining(), side.getStandingAtDeclare(), side.getPicksSubmittedAt());
    }
}
