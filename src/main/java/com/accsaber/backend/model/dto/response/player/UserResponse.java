package com.accsaber.backend.model.dto.response.player;

import java.time.Instant;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserResponse {

    Long id;
    String name;
    String avatarUrl;
    String country;
    Instant createdAt;
}
