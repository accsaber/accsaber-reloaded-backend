package com.accsaber.backend.model.dto.response.clan;

import java.util.Map;

import com.accsaber.backend.model.entity.clan.war.ClanWarTeam;

public record ClanWarForecastResponse(Map<ClanWarTeam, Double> worth) {
}
