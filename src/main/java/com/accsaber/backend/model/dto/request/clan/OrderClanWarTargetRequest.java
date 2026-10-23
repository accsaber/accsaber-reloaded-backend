package com.accsaber.backend.model.dto.request.clan;

import com.accsaber.backend.model.entity.clan.war.ClanWarOrder;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record OrderClanWarTargetRequest(
        @NotNull @Pattern(regexp = "^\\d+$") String userId,
        @NotNull ClanWarOrder order,
        @Pattern(regexp = "^\\d+$") String targetUserId) {
}
