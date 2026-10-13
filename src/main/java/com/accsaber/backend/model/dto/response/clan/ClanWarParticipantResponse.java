package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.clan.war.ClanWarParticipant;

public record ClanWarParticipantResponse(
        PlayerRef player,
        PublicClanResponse clan,
        PlayerRef target,
        double skill,
        Boolean inBand,
        double standingWeight,
        double guard,
        int guardCycle,
        int breaksSuffered,
        double contribution,
        Instant joinedAt,
        Instant leftAt) {

    public static ClanWarParticipantResponse of(ClanWarParticipant participant, PublicClanResponse clan, Double skill,
            Boolean inBand) {
        return new ClanWarParticipantResponse(PlayerRef.of(participant.getUser()), clan,
                PlayerRef.of(participant.getTarget()), skill == null ? 0.0 : skill, inBand,
                participant.getStandingWeight(), participant.getGuard(),
                participant.getGuardCycle(), participant.getBreaksSuffered(), participant.getContribution(),
                participant.getJoinedAt(), participant.getLeftAt());
    }
}
