package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.entity.clan.ClanSeason;

public record ClanSeasonResponse(UUID id, String name, String slug, Instant startsAt, Instant endsAt,
        Instant closedAt) {

    public static ClanSeasonResponse of(ClanSeason season) {
        return new ClanSeasonResponse(season.getId(), season.getName(), season.getSlug(), season.getStartsAt(),
                season.getEndsAt(), season.getClosedAt());
    }
}
