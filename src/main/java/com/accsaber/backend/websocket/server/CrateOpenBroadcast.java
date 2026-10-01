package com.accsaber.backend.websocket.server;

import com.accsaber.backend.model.dto.response.item.CrateOpenResponse;
import com.accsaber.backend.model.dto.response.common.PlayerRef;

public record CrateOpenBroadcast(String type, PlayerRef player, CrateOpenResponse open) {
}
