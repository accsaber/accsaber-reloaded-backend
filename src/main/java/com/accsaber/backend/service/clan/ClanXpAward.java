package com.accsaber.backend.service.clan;

import com.accsaber.backend.model.entity.clan.ClanXpSource;

public record ClanXpAward(double rawAmount, ClanXpSource source, String sourceId, boolean rosterScaled) {
}
