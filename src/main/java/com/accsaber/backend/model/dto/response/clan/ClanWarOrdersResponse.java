package com.accsaber.backend.model.dto.response.clan;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.clan.war.ClanWarOrder;
import com.accsaber.backend.model.entity.clan.war.ClanWarTargeting;

public record ClanWarOrdersResponse(
        ClanWarTargeting targeting,
        ClanWarOrder order,
        PlayerRef target) {
}
