package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.entity.clan.ClanStandingEvent;
import com.accsaber.backend.model.entity.clan.ClanStandingSource;

public record ClanStandingEventResponse(UUID id, ClanStandingSource source, String sourceId, double amount,
        Instant createdAt) {

    public static ClanStandingEventResponse of(ClanStandingEvent event) {
        return new ClanStandingEventResponse(event.getId(), event.getSource(), event.getSourceId(),
                event.getAmount(), event.getCreatedAt());
    }
}
