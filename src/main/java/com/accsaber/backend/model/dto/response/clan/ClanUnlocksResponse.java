package com.accsaber.backend.model.dto.response.clan;

import java.util.List;
import java.util.Map;

import com.accsaber.backend.model.dto.response.item.ItemResponse;
import com.accsaber.backend.model.entity.clan.ClanCapacity;
import com.accsaber.backend.model.entity.clan.war.ClanArena;
import com.accsaber.backend.model.entity.clan.war.ClanRuleset;

public record ClanUnlocksResponse(
        Map<ClanCapacity, Integer> capacities,
        List<ClanArena> arenas,
        List<ClanRuleset> rulesets,
        List<ItemResponse> cosmetics) {
}
