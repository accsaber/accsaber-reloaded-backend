package com.accsaber.backend.model.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClanArenaSpec(
        UUID categoryId,
        Double complexityMin,
        Double complexityMax,
        int poolSize,
        int attackerPicks,
        int defenderPicks) {
}
