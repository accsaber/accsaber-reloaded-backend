package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.clan.war.ClanWarTeam;

public record ClanWarSideResponse(
        PublicClanResponse clan,
        ClanWarTeam team,
        PlayerRef lead,
        double stake,
        double stakeRemaining,
        double standingAtDeclare,
        Instant picksSubmittedAt,
        Instant joinedAt,
        Instant leftAt,
        boolean fallen) {
}
