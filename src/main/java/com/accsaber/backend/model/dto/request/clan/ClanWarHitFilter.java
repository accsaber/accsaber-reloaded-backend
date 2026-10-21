package com.accsaber.backend.model.dto.request.clan;

import java.util.UUID;

public record ClanWarHitFilter(Long userId, UUID clanId, Boolean chip) {
}
