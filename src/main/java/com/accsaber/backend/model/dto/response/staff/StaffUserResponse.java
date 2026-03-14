package com.accsaber.backend.model.dto.response.staff;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.model.entity.staff.StaffUserStatus;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StaffUserResponse {

    UUID id;
    String username;
    String email;
    StaffRole role;
    StaffUserStatus status;
    Long userId;
    boolean active;
    Instant createdAt;
}
