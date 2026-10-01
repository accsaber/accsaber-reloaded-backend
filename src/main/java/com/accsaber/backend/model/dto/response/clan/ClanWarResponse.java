package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.dto.ClanArenaSpec;
import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.clan.war.ClanArena;
import com.accsaber.backend.model.entity.clan.war.ClanRuleset;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarOutcome;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;

public record ClanWarResponse(
        UUID id,
        ClanArena arena,
        ClanArenaSpec arenaSpec,
        ClanRuleset ruleset,
        ClanWarStatus status,
        ClanWarOutcome outcome,
        PlayerRef declaredBy,
        ClanWarSideResponse attacker,
        ClanWarSideResponse defender,
        Instant declaredAt,
        Instant picksDueAt,
        Instant startsAt,
        Instant endedAt) {

    public static ClanWarResponse of(ClanWar war, List<ClanWarSideResponse> sides) {
        return new ClanWarResponse(war.getId(), war.getArena(), war.getArenaSpec(), war.getRuleset(),
                war.getStatus(), war.getOutcome(), PlayerRef.of(war.getDeclaredBy()),
                side(sides, war.getAttackerClan().getId()), side(sides, war.getDefenderClan().getId()),
                war.getDeclaredAt(), war.getPicksDueAt(), war.getStartsAt(), war.getEndedAt());
    }

    private static ClanWarSideResponse side(List<ClanWarSideResponse> sides, UUID clanId) {
        return sides.stream().filter(side -> side.clan().id().equals(clanId)).findFirst().orElse(null);
    }
}
