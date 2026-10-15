package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.accsaber.backend.model.dto.ClanArenaSpec;
import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.clan.war.ClanArena;
import com.accsaber.backend.model.entity.clan.war.ClanRuleset;
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
        ClanWarTeamResponse attack,
        ClanWarTeamResponse defend,
        Instant declaredAt,
        Instant picksDueAt,
        Instant startsAt,
        Instant endedAt) {
    public List<UUID> clanIds() {
        return Stream.of(attack, defend)
                .flatMap(team -> team.sides().stream())
                .map(side -> side.clan().id())
                .toList();
    }
}
