package com.accsaber.backend.model.dto.request.clan;

import java.util.Set;

import com.accsaber.backend.model.entity.clan.ClanMedia;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ModerateClanRequest(
        @NotNull @Valid UpdateClanRequest changes,
        Set<ClanMedia> removeMedia,
        @NotBlank @Size(max = 500) String reason) {

    public ModerateClanRequest {
        removeMedia = removeMedia == null ? Set.of() : removeMedia;
    }
}
