package com.accsaber.backend.model.dto.response.clan;

import com.accsaber.backend.model.dto.response.milestone.LevelResponse;

public record ClanLevelResponse(LevelResponse progress, ClanUnlocksResponse unlocked) {
}
