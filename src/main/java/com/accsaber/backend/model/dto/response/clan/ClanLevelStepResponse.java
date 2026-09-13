package com.accsaber.backend.model.dto.response.clan;

public record ClanLevelStepResponse(int level, double totalXpRequired, ClanUnlocksResponse unlocks) {
}
