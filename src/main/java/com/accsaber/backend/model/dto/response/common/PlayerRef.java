package com.accsaber.backend.model.dto.response.common;

import com.accsaber.backend.model.dto.response.clan.ClanMembershipResponse;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;

public record PlayerRef(String id, String name, String avatarUrl, String cdnAvatarUrl, String country,
        PublicClanResponse clan, ClanMembershipResponse membership) {
}
