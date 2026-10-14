package com.accsaber.backend.model.dto.request.user;

import jakarta.validation.constraints.NotNull;

public record MergeUsersRequest(
        @NotNull Long primaryUserId,
        @NotNull Long secondaryUserId,
        String reason) {
}
