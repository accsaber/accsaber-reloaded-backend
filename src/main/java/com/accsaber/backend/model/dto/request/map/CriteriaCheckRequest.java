package com.accsaber.backend.model.dto.request.map;

import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.entity.map.Difficulty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CriteriaCheckRequest(
        @Pattern(regexp = "[0-9a-fA-F]{40}") String songHash,
        @NotEmpty @Size(max = 10) @Valid List<Check> checks) {

    public record Check(@NotNull Difficulty difficulty, @NotNull UUID categoryId) {
    }
}
