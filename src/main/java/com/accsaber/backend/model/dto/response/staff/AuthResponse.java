package com.accsaber.backend.model.dto.response.staff;

import com.accsaber.backend.model.entity.staff.StaffRole;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AuthResponse {

    String accessToken;
    String refreshToken;
    long expiresIn;
    StaffRole role;
}
