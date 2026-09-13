package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.entity.clan.ClanXpGrant;
import com.accsaber.backend.model.entity.clan.ClanXpSource;

public record ClanXpGrantResponse(UUID id, ClanXpSource source, String sourceId, double rawAmount,
        double rosterFactor, double amount, Instant createdAt) {

    public static ClanXpGrantResponse of(ClanXpGrant grant) {
        return new ClanXpGrantResponse(grant.getId(), grant.getSource(), grant.getSourceId(), grant.getRawAmount(),
                grant.getRosterFactor(), grant.getAmount(), grant.getCreatedAt());
    }
}
