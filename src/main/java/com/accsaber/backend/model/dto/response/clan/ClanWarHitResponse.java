package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.dto.response.map.PublicMapDifficultyResponse;
import com.accsaber.backend.model.entity.clan.war.ClanWarHit;

public record ClanWarHitResponse(
        UUID id,
        PlayerRef attacker,
        PlayerRef victim,
        int victimCycle,
        PublicMapDifficultyResponse difficulty,
        boolean missingScore,
        double damage,
        double guardAfter,
        boolean broke,
        double standingMoved,
        Double xpAwarded,
        Instant createdAt) {

    public static ClanWarHitResponse of(ClanWarHit hit, PublicMapDifficultyResponse difficulty) {
        return new ClanWarHitResponse(hit.getId(), PlayerRef.of(hit.getAttacker()), PlayerRef.of(hit.getVictim()),
                hit.getVictimCycle(), difficulty, hit.getVictimScore() == null, hit.getDamage(), hit.getGuardAfter(),
                hit.isBroke(), hit.getStandingMoved(), hit.getXpAwarded(), hit.getCreatedAt());
    }
}
