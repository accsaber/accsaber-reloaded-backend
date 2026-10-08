package com.accsaber.backend.model.dto.response.clan;

import java.util.Map;

import com.accsaber.backend.model.dto.response.milestone.LevelResponse;
import com.accsaber.backend.model.entity.clan.ClanXpSource;

public record ClanLevelResponse(LevelResponse progress, ClanUnlocksResponse unlocked, double rosterFactor,
        Map<ClanXpSource, Double> seasonXpBySource) {
}
