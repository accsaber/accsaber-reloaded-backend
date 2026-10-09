package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.entity.clan.ClanXpSource;
import com.accsaber.backend.repository.clan.ClanXpGrantRepository.HistoryRowView;

public record ClanXpGrantResponse(UUID id, ClanXpSource source, String sourceId, double rawAmount,
        double rosterFactor, double amount, Instant createdAt, long count) {

    public static ClanXpGrantResponse of(HistoryRowView row) {
        return new ClanXpGrantResponse(row.getId(), ClanXpSource.valueOf(row.getSource()), row.getSourceId(),
                row.getRawAmount(), row.getRosterFactor(), row.getAmount(), row.getCreatedAt(), row.getGrants());
    }
}
