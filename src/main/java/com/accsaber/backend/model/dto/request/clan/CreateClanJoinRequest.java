package com.accsaber.backend.model.dto.request.clan;

import jakarta.validation.constraints.Pattern;

public record CreateClanJoinRequest(
        @Pattern(regexp = "^\\d+$") String userId) {
}
