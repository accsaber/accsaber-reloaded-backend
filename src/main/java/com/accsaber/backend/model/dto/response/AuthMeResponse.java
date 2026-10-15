package com.accsaber.backend.model.dto.response;

import java.util.List;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.clan.ClanRole;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AuthMeResponse {

    PlayerRef player;
    ClanRole clanRole;
    String supporterTier;
    boolean banned;
    List<OauthConnectionSummary> connections;
    StaffContext staff;

    @Value
    @Builder
    public static class OauthConnectionSummary {
        String provider;
        String providerUserId;
        String providerUsername;
        String providerAvatarUrl;
    }

    @Value
    @Builder
    public static class StaffContext {
        String staffId;
        String role;
        String status;
    }
}
