package com.accsaber.backend.model.dto.request.map;

import com.accsaber.backend.model.entity.map.Difficulty;

public record MapHubLookup(String beatsaverCode, Difficulty difficulty, String characteristic) {
}
