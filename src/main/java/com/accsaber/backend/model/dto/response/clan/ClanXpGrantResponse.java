package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.entity.clan.ClanXpSource;

public record ClanXpGrantResponse(UUID id, ClanXpSource source, String sourceId, double rawAmount,
        double rosterFactor, double amount, Instant createdAt, long count) {
}
