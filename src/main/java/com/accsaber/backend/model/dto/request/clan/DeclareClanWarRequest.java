package com.accsaber.backend.model.dto.request.clan;

import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.entity.clan.war.ClanArena;
import com.accsaber.backend.model.entity.clan.war.ClanRuleset;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class DeclareClanWarRequest {

    @NotNull
    private UUID clanId;

    @NotNull
    private ClanArena arena;

    @NotNull
    private ClanRuleset ruleset;

    private UUID categoryId;

    @Positive
    private Double complexityMin;

    @Positive
    private Double complexityMax;

    @NotNull
    private List<UUID> mapDifficultyIds;
}
