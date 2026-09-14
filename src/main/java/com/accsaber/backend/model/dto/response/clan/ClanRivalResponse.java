package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.clan.ClanRival;

public record ClanRivalResponse(PublicClanResponse clan, boolean incoming, PlayerRef declaredBy, Instant since) {

    public static ClanRivalResponse of(ClanRival rival, PublicClanResponse clan, boolean incoming) {
        return new ClanRivalResponse(clan, incoming, PlayerRef.of(rival.getDeclaredBy()), rival.getUpdatedAt());
    }
}
