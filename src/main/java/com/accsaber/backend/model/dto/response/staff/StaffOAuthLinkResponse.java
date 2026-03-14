package com.accsaber.backend.model.dto.response.staff;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StaffOAuthLinkResponse {

    UUID id;
    String provider;
    String providerUsername;
    String providerAvatarUrl;
    Instant linkedAt;
}
