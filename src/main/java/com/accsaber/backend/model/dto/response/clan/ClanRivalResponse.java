package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;

import com.accsaber.backend.model.dto.response.common.PlayerRef;

public record ClanRivalResponse(PublicClanResponse clan, boolean incoming, PlayerRef declaredBy, Instant since) {
}
