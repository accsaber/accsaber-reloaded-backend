package com.accsaber.backend.model.dto.response.clan;

import java.util.List;
import java.util.Map;

import com.accsaber.backend.model.entity.clan.war.ClanWarTeam;

public record ClanWarDetailResponse(ClanWarResponse war, List<ClanWarPoolEntryResponse> pool,
        List<ClanWarTimelinePointResponse> timeline, ClanWarStrikeRulesResponse strikeRules,
        Map<ClanWarTeam, ClanWarLastStandResponse> lastStand) {
}
