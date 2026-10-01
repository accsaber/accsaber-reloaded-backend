package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.clan.ClanRole;

public record ClanMemberResponse(PlayerRef player, ClanRole role, Instant joinedAt, boolean online,
        Instant lastPlayedAt) {
}
