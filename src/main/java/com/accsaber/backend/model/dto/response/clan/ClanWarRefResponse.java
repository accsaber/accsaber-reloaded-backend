package com.accsaber.backend.model.dto.response.clan;

import java.util.UUID;

import com.accsaber.backend.model.entity.clan.war.ClanArena;
import com.accsaber.backend.model.entity.clan.war.ClanRuleset;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarOutcome;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;

public record ClanWarRefResponse(UUID id, ClanArena arena, ClanRuleset ruleset, ClanWarStatus status,
        ClanWarOutcome outcome) {

    public static ClanWarRefResponse of(ClanWar war) {
        if (war == null) {
            return null;
        }
        return new ClanWarRefResponse(war.getId(), war.getArena(), war.getRuleset(), war.getStatus(),
                war.getOutcome());
    }
}
