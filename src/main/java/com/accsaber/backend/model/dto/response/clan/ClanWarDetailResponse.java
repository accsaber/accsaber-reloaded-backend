package com.accsaber.backend.model.dto.response.clan;

import java.util.List;

public record ClanWarDetailResponse(ClanWarResponse war, List<ClanWarPoolEntryResponse> pool) {
}
