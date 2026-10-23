package com.accsaber.backend.model.dto.request.clan;

import com.accsaber.backend.model.entity.clan.war.ClanWarTargeting;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ChooseClanWarTargetRequest(
        @NotNull ClanWarTargeting targeting,
        @Pattern(regexp = "^\\d+$") String userId) {
}
