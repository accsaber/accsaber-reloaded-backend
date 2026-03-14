package com.accsaber.backend.model.dto.request.staff;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OAuthLinkRequest {

    @NotBlank
    private String provider;

    @NotBlank
    private String providerUserId;

    private String providerUsername;

    private String providerAvatarUrl;
}
