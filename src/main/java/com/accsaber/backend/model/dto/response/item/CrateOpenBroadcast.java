package com.accsaber.backend.model.dto.response.item;

import com.accsaber.backend.model.dto.response.common.PlayerRef;

public record CrateOpenBroadcast(String type, PlayerRef player, CrateOpenResponse open) {
}
