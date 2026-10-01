package com.accsaber.backend.model.dto.response;

import java.util.List;

import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AuthMeResponse {

    String userId;
    String name;
    String avatarUrl;
    String cdnAvatarUrl;
    PublicClanResponse clan;
    String country;
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
