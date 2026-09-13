package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.dto.response.milestone.LevelResponse;

public record ClanResponse(
        PublicClanResponse clan,
        String description,
        boolean acceptingRequests,
        LevelResponse level,
        double standing,
        long memberCount,
        int memberCap,
        PlayerRef founder,
        Instant createdAt) {
}
