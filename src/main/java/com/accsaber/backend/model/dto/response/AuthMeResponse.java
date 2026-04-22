package com.accsaber.backend.model.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AuthMeResponse {

    Long userId;
    String name;
    String avatarUrl;
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
