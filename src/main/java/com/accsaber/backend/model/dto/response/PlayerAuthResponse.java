package com.accsaber.backend.model.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PlayerAuthResponse {

    String accessToken;
    String refreshToken;
    long expiresIn;
    Long userId;
}
