package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;
import java.util.UUID;

public record ClanWarTimelinePointResponse(Instant at, UUID clanId, double damage, long hits, long breaks,
        double standingMoved) {
}
