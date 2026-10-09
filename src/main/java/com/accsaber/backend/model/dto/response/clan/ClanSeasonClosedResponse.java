package com.accsaber.backend.model.dto.response.clan;

import java.util.List;

public record ClanSeasonClosedResponse(ClanSeasonResponse season, List<ClanStandingResponse> top) {
}
