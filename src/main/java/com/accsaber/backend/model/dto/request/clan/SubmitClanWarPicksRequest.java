package com.accsaber.backend.model.dto.request.clan;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class SubmitClanWarPicksRequest {

    @NotEmpty
    private List<UUID> mapDifficultyIds;
}
